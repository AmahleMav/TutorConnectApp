package models

import com.google.firebase.Timestamp

data class Booking(
    var bookingId: String = "",           // Firestore document ID
    var tutorId: String = "",             // Tutor UID
    var tutorName: String = "",           // Tutor display name
    var tutorEmail: String = "",          // Tutor email
    var studentId: String = "",           // Student UID
    var studentName: String = "",         // Student display name
    var studentEmail: String = "",        // Student email
    var day: String = "",                 // Day of session (e.g., Monday)
    var date: String = "",                // Date of session (e.g., 30/10/2025)
    var startHour: String = "",           // Start hour (e.g., "08:00")
    var endHour: String = "",             // End hour (e.g., "09:00")
    var time: String = "",                // Full time string: "08:00 - 09:00"
    var sessionType: String = "",         // "One-on-One" or "Group"
    var isGroup: Boolean = false,
    var isCancelled: Boolean = false,
    var isCompleted: Boolean = false,
    var status: String = "Upcoming",
    var hour: Int = 0,
    var amountEarned: Double? = 0.0,
    var pricePaid: Double? = 0.0,        // Amount student paid
    var bookingDate: Timestamp? = null,
    var loggedAt: Timestamp? = null,
    var hoursWorked: Double = 0.0,

    var rating: Double? = null,
    var comment: String? = null,
    var isRated: Boolean = false,

    var studentAttended: Boolean = false,
    var attendanceTimestamp: Timestamp? = null,
    var completionTimestamp: Timestamp? = null,

    var updatedAt: Timestamp? = null,
    var createdAt: Timestamp? = null
)
