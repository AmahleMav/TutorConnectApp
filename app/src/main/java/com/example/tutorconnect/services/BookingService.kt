package com.example.tutorconnect.services

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import models.Booking

class BookingService {

    private val db = FirebaseFirestore.getInstance()

    // Build deterministic booking ID (unique per tutor, date, hour, and student)
    private fun buildBookingDocId(tutorId: String, date: String, hour: Int, studentId: String): String {
        val safeTutor = tutorId.replace("\\s+".toRegex(), "_")
        val safeDate = date.replace("[^0-9-]".toRegex(), "_")
        val safeStudent = studentId.replace("\\s+".toRegex(), "_")
        return "booking_${safeTutor}_${safeDate}_${hour}_${safeStudent}"
    }


    // Transactional booking creation (atomic + safe)
    suspend fun createBookingTransactional(booking: Booking): BookingResult {
        if (booking.tutorId.isBlank() || booking.studentId.isBlank() || booking.date.isBlank()) {
            return BookingResult.Failure("Invalid booking data.")
        }

        val bookingId = buildBookingDocId(booking.tutorId, booking.date, booking.hour, booking.studentId)
        booking.bookingId = bookingId

        val tutorRef = db.collection("TutorProfiles").document(booking.tutorId)
        val bookingRef = db.collection("Bookings").document(bookingId)
        val tutorBookingRef = tutorRef.collection("Bookings").document(bookingId)

        return try {
            db.runTransaction { transaction ->

                // Check for existing booking
                val existingBooking = transaction.get(bookingRef)
                if (existingBooking.exists()) {
                    throw IllegalStateException("You already booked this slot.")
                }

                // Get tutor profile
                val tutorSnapshot = transaction.get(tutorRef)
                if (!tutorSnapshot.exists()) {
                    throw IllegalStateException("Tutor profile not found.")
                }

                // Find and lock available slot
                val weeklyAvailability = tutorSnapshot.get("WeeklyAvailability") as? Map<String, Any>
                    ?: throw IllegalStateException("Tutor availability not found.")

                val day = booking.day
                val slotsForDay = weeklyAvailability[day] as? List<Map<String, Any>>
                    ?: throw IllegalStateException("No slots found for $day")

                val updatedSlots = slotsForDay.toMutableList()
                var found = false

                for (i in updatedSlots.indices) {
                    val slotMap = updatedSlots[i].toMutableMap()
                    val hourVal = (slotMap["Hour"] as? Number)?.toInt() ?: continue
                    val isAvailable = slotMap["IsAvailable"] as? Boolean ?: false

                    if (hourVal == booking.hour) {
                        if (!isAvailable) throw IllegalStateException("This slot has already been booked.")
                        slotMap["IsAvailable"] = false
                        updatedSlots[i] = slotMap.toMap()
                        found = true
                        break
                    }
                }

                if (!found) throw IllegalStateException("Requested time slot not available.")

                // Update tutor’s availability
                transaction.update(tutorRef, "WeeklyAvailability.$day", updatedSlots)

                // Create booking document
                val now = Timestamp.now()
                val bookingDoc = mapOf(
                    "BookingId" to booking.bookingId,
                    "TutorId" to booking.tutorId,
                    "TutorName" to booking.tutorName,
                    "StudentId" to booking.studentId,
                    "StudentName" to booking.studentName,
                    "Day" to booking.day,
                    "Date" to booking.date,
                    "Hour" to booking.hour,
                    "Time" to booking.time,
                    "SessionType" to booking.sessionType,
                    "IsGroup" to booking.isGroup,
                    "IsCancelled" to false,
                    "IsCompleted" to false,
                    "isRated" to false,
                    "rating" to null,
                    "comment" to null,
                    "StudentAttended" to false,
                    "Status" to "Pending",
                    "BookingDate" to now,
                    "Timestamp" to now
                )

                // Save booking in both root and tutor subcollection
                transaction.set(bookingRef, bookingDoc)
                transaction.set(tutorBookingRef, bookingDoc)

                null
            }.await()

            Log.d("BookingService", "✅ Booking created successfully for tutor ${booking.tutorId}")
            BookingResult.Success("Booking created successfully!")

        } catch (e: Exception) {
            Log.e("BookingService", "❌ Booking transaction failed: ${e.message}", e)
            BookingResult.Failure(e.message ?: "Unknown error occurred.")
        }
    }

    // Fetch all bookings for a specific student
    suspend fun getStudentBookings(studentId: String?): List<Booking> {
        val bookings = mutableListOf<Booking>()
        if (studentId.isNullOrBlank()) return bookings

        try {
            val snapshot = db.collection("Bookings")
                .whereEqualTo("StudentId", studentId)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val booking = mapBookingFromData(data)
                bookings.add(booking)
            }

            Log.d("BookingService", "📦 Found ${bookings.size} student bookings")

        } catch (e: Exception) {
            Log.e("BookingService", "❌ getStudentBookings failed: ${e.message}", e)
        }

        return bookings
    }

    // Fetch all bookings for a specific tutor
    suspend fun getTutorBookings(tutorId: String?): List<Booking> {
        val bookings = mutableListOf<Booking>()
        if (tutorId.isNullOrBlank()) return bookings

        try {
            val snapshot = db.collection("Bookings")
                .whereEqualTo("TutorId", tutorId)
                .get()
                .await()

            Log.d("BookingService", "📦 Found ${snapshot.size()} bookings in snapshot")

            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val booking = mapBookingFromData(data)

                // Raw Firestore document for reference
                Log.d("TutorBookingsFragment", "📘 Booking doc: $data")

                // Enhanced debug log for mapped Booking object
                Log.d(
                    "BookingDebug",
                    """
                    🧾 Booking ${booking.bookingId}
                    | StudentAttended=${booking.studentAttended}
                    | Status=${booking.status}
                    | IsCompleted=${booking.isCompleted}
                    | Time=${booking.time}
                    | Tutor=${booking.tutorName}
                    | Student=${booking.studentName}
                    """.trimIndent()
                )

                bookings.add(booking)
            }

            Log.d("BookingService", "✅ Successfully fetched ${bookings.size} bookings for tutorId=$tutorId")

        } catch (e: Exception) {
            Log.e("BookingService", "❌ getTutorBookings failed: ${e.message}", e)
        }

        return bookings
    }

    // Update a booking's status (for tutors or system actions)
    suspend fun updateBookingStatus(bookingId: String, newStatus: String): Boolean {
        return try {
            val bookingRef = db.collection("Bookings").document(bookingId)
            bookingRef.update(
                "Status", newStatus,
                "UpdatedAt", FieldValue.serverTimestamp()
            ).await()
            Log.d("BookingService", "✅ Booking status updated to $newStatus")
            true
        } catch (e: Exception) {
            Log.e("BookingService", "❌ Failed to update booking status: ${e.message}", e)
            false
        }
    }

    // Helper: Safely map Firestore data to Booking model
    private fun mapBookingFromData(data: Map<String, Any>): Booking {
        return Booking(
            bookingId = data["BookingId"] as? String ?: "",
            tutorId = data["TutorId"] as? String ?: "",
            tutorName = data["TutorName"] as? String ?: "",
            studentId = data["StudentId"] as? String ?: "",
            studentName = data["StudentName"] as? String ?: "",
            day = data["Day"] as? String ?: "",
            date = data["Date"] as? String ?: "",
            hour = (data["Hour"] as? Long)?.toInt() ?: 0,
            time = data["Time"] as? String ?: "",
            sessionType = data["SessionType"] as? String ?: "",
            isGroup = data["IsGroup"] as? Boolean ?: false,
            isCancelled = data["IsCancelled"] as? Boolean ?: false,
            isCompleted = data["IsCompleted"] as? Boolean ?: false,
            status = data["Status"] as? String ?: "Pending",
            rating = (data["rating"] as? Number)?.toDouble()
                ?: (data["Rating"] as? Number)?.toDouble(),
            comment = data["comment"] as? String
                ?: data["Comment"] as? String,
            isRated = data["isRated"] as? Boolean
                ?: data["IsRated"] as? Boolean ?: false,
            studentAttended = data["StudentAttended"] as? Boolean ?: false,
            attendanceTimestamp = data["AttendanceTimestamp"] as? Timestamp,
            completionTimestamp = data["CompletionTimestamp"] as? Timestamp,
            updatedAt = data["UpdatedAt"] as? Timestamp,
            createdAt = data["BookingDate"] as? Timestamp
        )
    }
}
