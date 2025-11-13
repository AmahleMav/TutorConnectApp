package com.example.tutorconnect.utils

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import models.Booking
import java.time.*
import kotlin.math.round

suspend fun fetchCompletedBookingsForTutor(
    db: FirebaseFirestore,
    tutorId: String
): List<Booking> {
    return try {
        val snapshot = db.collection("Bookings")
            .whereEqualTo("TutorId", tutorId)
            .whereEqualTo("IsCompleted", true)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(Booking::class.java)?.apply {
                bookingId = doc.id
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}



data class TutorComputedSummary(
    val totalHours: Double,
    val completedSessions: Int,
    val averageRating: Double,
    val last7DaysHours: List<Double>
)

@RequiresApi(Build.VERSION_CODES.O)
fun computeTutorSummary(bookings: List<Booking>): TutorComputedSummary {
    if (bookings.isEmpty()) {
        return TutorComputedSummary(0.0, 0, 0.0, List(7) { 0.0 })
    }

    val zone = ZoneId.of("Africa/Johannesburg")
    val today = LocalDate.now(zone)
    val startDate = today.minusDays(6)

    var totalHours = 0.0
    val ratings = mutableListOf<Double>()
    val dailyMap = mutableMapOf<LocalDate, Double>()

    for (b in bookings) {
        totalHours += b.hoursWorked
        b.rating?.let { ratings.add(it) }

        val timestamp = b.bookingDate ?: b.completionTimestamp ?: b.loggedAt ?: b.updatedAt
        timestamp?.toDate()?.let {
            val date = it.toInstant().atZone(zone).toLocalDate()
            if (!date.isBefore(startDate) && !date.isAfter(today)) {
                dailyMap[date] = (dailyMap[date] ?: 0.0) + b.hoursWorked
            }
        }
    }

    val last7Days = (0..6).map { offset ->
        val date = startDate.plusDays(offset.toLong())
        dailyMap[date] ?: 0.0
    }

    val avgRating = if (ratings.isNotEmpty()) {
        round((ratings.sum() / ratings.size) * 10) / 10.0
    } else 0.0

    return TutorComputedSummary(
        totalHours = totalHours,
        completedSessions = bookings.size,
        averageRating = avgRating,
        last7DaysHours = last7Days
    )
}
