package com.example.tutorconnect.Views.tutor.fragments

import Views.tutor.fragments.TutorBookingsFragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TutorHomeFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentTutorId = auth.currentUser?.uid ?: ""

    private lateinit var btnViewBookings: Button
    private lateinit var txtUpcomingSessions: TextView

    private var bookingsListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_home, container, false)

        txtUpcomingSessions = view.findViewById(R.id.txtUpcomingSessions)
        btnViewBookings = view.findViewById(R.id.btnViewBookings)

        // Start real-time listener that counts bookings with status "Upcoming"
        startUpcomingCountListener()

        btnViewBookings.setOnClickListener {
            try {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.tutor_fragment_container, TutorBookingsFragment())
                    .addToBackStack(null)
                    .commit()
            } catch (e: Exception) {
                Log.e("TutorHomeFragment", "Failed to open TutorBookingsFragment: ${e.message}", e)
            }
        }

        return view
    }

    private fun startUpcomingCountListener() {
        if (currentTutorId.isEmpty()) {
            txtUpcomingSessions.text = "0"
            return
        }

        // Listen for any changes to bookings for this tutor, then count those with status == "Upcoming"
        bookingsListener = db.collection("Bookings")
            .whereEqualTo("TutorId", currentTutorId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("TutorHomeFragment", "Error listening for bookings: ${error.message}", error)
                    txtUpcomingSessions.text = "0"
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    txtUpcomingSessions.text = "0"
                    return@addSnapshotListener
                }

                var count = 0
                for (doc in snapshots.documents) {
                    val status = (doc.getString("Status") ?: doc.getString("status") ?: "")
                    if (status.equals("Upcoming", ignoreCase = true)) {
                        count++
                    }
                }

                txtUpcomingSessions.text = count.toString()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bookingsListener?.remove()
    }
}
