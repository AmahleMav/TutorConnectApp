package Views.student.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import Views.student.adapters.StudentBookingsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import models.Booking
import java.text.SimpleDateFormat
import java.util.Locale

class BookingsFragment : Fragment() {

    private lateinit var recyclerBookings: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabUpcoming: TextView
    private lateinit var tabPast: TextView

    private lateinit var adapter: StudentBookingsAdapter
    private val allBookings = mutableListOf<Booking>()
    private val bookingsList = mutableListOf<Booking>()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_bookings, container, false)

        recyclerBookings = view.findViewById(R.id.recyclerBookings)
        txtEmpty = view.findViewById(R.id.txtEmptyBookings)
        progressBar = view.findViewById(R.id.progressBar)
        tabUpcoming = view.findViewById(R.id.tabUpcoming)
        tabPast = view.findViewById(R.id.tabPast)

        recyclerBookings.layoutManager = LinearLayoutManager(requireContext())
        adapter = StudentBookingsAdapter(bookingsList)
        recyclerBookings.adapter = adapter

        tabUpcoming.setOnClickListener { showUpcoming() }
        tabPast.setOnClickListener { showPast() }

        fetchBookings()
        return view
    }

    private fun fetchBookings() {
        val studentId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        txtEmpty.visibility = View.GONE

        db.collection("Bookings")
            .whereEqualTo("StudentId", studentId)
            .get()
            .addOnSuccessListener { result ->
                allBookings.clear()
                if (result.isEmpty) {
                    showEmpty("No bookings yet")
                    return@addOnSuccessListener
                }

                val bookingDocs = result.documents
                var processedCount = 0

                for (doc in bookingDocs) {
                    val tutorId = doc.getString("TutorId") ?: ""
                    val startHourNum = doc.get("Hour") as? Number
                    val endHourNum = doc.get("EndHour") as? Number
                    val startHour = startHourNum?.toInt()?.let { "$it:00" } ?: ""
                    val endHour = endHourNum?.toInt()?.let { "$it:00" } ?: ""
                    val time = if (startHour.isNotEmpty() && endHour.isNotEmpty()) "$startHour - $endHour" else "N/A"

                    val isGroup = doc.getBoolean("IsGroup") ?: false
                    val sessionType = if (isGroup) "Group" else "One-on-One" // ← keep Type
                    val date = doc.getTimestamp("BookingDate")?.toDate()?.let { sdf.format(it) } ?: ""
                    val day = doc.getString("Day") ?: ""
                    val status = doc.getString("Status") ?: "Upcoming"
                    val pricePaid = (doc.get("PricePaid") as? Number)?.toDouble() ?: 0.0
                    val isCompleted = doc.getBoolean("IsCompleted") ?: false

                    db.collection("Tutors").document(tutorId).get()
                        .addOnSuccessListener { tutorDoc ->
                            val tutorName = tutorDoc.getString("Name") ?: "N/A"
                            val tutorSurname = tutorDoc.getString("Surname") ?: ""
                            val tutorFullName = "$tutorName $tutorSurname".trim()
                            val tutorEmail = tutorDoc.getString("Email") ?: "N/A"

                            val booking = Booking(
                                bookingId = doc.id,
                                tutorName = tutorFullName,
                                tutorEmail = tutorEmail,
                                startHour = startHour,
                                endHour = endHour,
                                time = time,
                                sessionType = sessionType,
                                date = date,
                                day = day,
                                status = status,
                                pricePaid = pricePaid,
                                isCompleted = isCompleted
                            )

                            allBookings.add(booking)
                            processedCount++

                            if (processedCount == bookingDocs.size) {
                                showUpcoming()
                                progressBar.visibility = View.GONE
                            }
                        }

                        .addOnFailureListener { e ->
                            processedCount++
                            Log.e("BookingsFragment", "Tutor fetch failed: ${e.message}", e)
                            if (processedCount == bookingDocs.size) {
                                showUpcoming()
                                progressBar.visibility = View.GONE
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("BookingsFragment", "Failed: ${e.message}", e)
                showEmpty("Failed to load bookings")
            }
    }


    private fun showUpcoming() {
        val upcoming = allBookings.filter { it.status.equals("Upcoming", true) }
        updateTabs(true)
        updateRecycler(upcoming)
    }

    private fun showPast() {
        val past = allBookings.filter {
            it.status.equals("Cancelled", true) || it.status.equals("Completed", true)
        }
        updateTabs(false)
        updateRecycler(past)
    }

    private fun updateTabs(upcomingSelected: Boolean) {
        if (upcomingSelected) {
            tabUpcoming.setBackgroundResource(R.drawable.tab_selected_bg)
            tabUpcoming.setTextColor(resources.getColor(android.R.color.white))
            tabPast.setBackgroundResource(R.drawable.tab_unselected_bg)
            tabPast.setTextColor(resources.getColor(android.R.color.black))
        } else {
            tabPast.setBackgroundResource(R.drawable.tab_selected_bg)
            tabPast.setTextColor(resources.getColor(android.R.color.white))
            tabUpcoming.setBackgroundResource(R.drawable.tab_unselected_bg)
            tabUpcoming.setTextColor(resources.getColor(android.R.color.black))
        }
    }

    private fun updateRecycler(list: List<Booking>) {
        bookingsList.clear()
        bookingsList.addAll(list)
        adapter.notifyDataSetChanged()

        txtEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        txtEmpty.text = if (list.isEmpty()) "No bookings in this section" else ""
    }

    private fun showEmpty(message: String) {
        progressBar.visibility = View.GONE
        recyclerBookings.visibility = View.GONE
        txtEmpty.text = message
        txtEmpty.visibility = View.VISIBLE
    }
}
