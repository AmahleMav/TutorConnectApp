package com.example.tutorconnect.services

/**
 * A simple sealed class to represent the result of a booking transaction.
 */
sealed class BookingResult {
    data class Success(val message: String = "Booking created successfully.") : BookingResult()
    data class Failure(val reason: String) : BookingResult()
}
