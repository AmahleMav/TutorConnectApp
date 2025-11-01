package com.example.tutorconnect.Views.tutor.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class TutorHomeFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentTutorId = auth.currentUser?.uid ?: ""

    private lateinit var ratingBarAverage: RatingBar
    private lateinit var txtTutorRating: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_home, container, false)

        ratingBarAverage = view.findViewById(R.id.ratingBarAverage)
        txtTutorRating = view.findViewById(R.id.txtTutorRating)

        fetchAndDisplayAverageRating()

        return view
    }

    private fun fetchAndDisplayAverageRating() {
        if (currentTutorId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get all bookings for this tutor
                val bookingsSnapshot = db.collection("Bookings")
                    .whereEqualTo("TutorId", currentTutorId)
                    .get()
                    .await()

                val ratings = bookingsSnapshot.documents.mapNotNull { it.getLong("Rating")?.toFloat() }

                val averageRating = if (ratings.isNotEmpty()) ratings.average().toFloat() else 0f
                val totalReviews = ratings.size

                withContext(Dispatchers.Main) {
                    ratingBarAverage.rating = averageRating
                    txtTutorRating.text = "⭐ %.1f ($totalReviews reviews)".format(averageRating)
                }
            } catch (e: Exception) {
                Log.e("TutorHomeFragment", "Error fetching tutor ratings: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    ratingBarAverage.rating = 0f
                    txtTutorRating.text = "⭐ 0.0 (0 reviews)"
                }
            }
        }
    }
}
