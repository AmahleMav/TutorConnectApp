package com.example.tutorconnect.services

import com.example.tutorconnect.models.Register
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class FirebaseService {

    private val db = FirebaseFirestore.getInstance()

    suspend fun addUser(user: Register): Result<String> {
        return try {
            val collectionName = if (user.role == "Tutor") "Tutors" else "Students"

            // Check for duplicate email
            val existing = db.collection(collectionName)
                .whereEqualTo("email", user.email)
                .get()
                .await()

            if (!existing.isEmpty) return Result.failure(Exception("A user with this email already exists."))

            // Generate a simple userId
            user.userId = collectionName.take(3).uppercase() + Random.nextInt(1000, 9999)

            // Add user to Firestore
            db.collection(collectionName)
                .add(user)
                .await()

            Result.success(user.userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
