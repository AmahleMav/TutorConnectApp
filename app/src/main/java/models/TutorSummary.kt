package com.example.tutorconnect.models // <- adjust package to match your project

data class TutorSummary(
    val totalHours: Double,
    val completedSessions: Int,
    val averageRating: Double
)
