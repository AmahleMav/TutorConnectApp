package Views.tutor.fragments

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
import Views.tutor.adapters.TutorBookingAdapter
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import models.Booking
import java.time.LocalTime
import java.time.Duration
import java.text.SimpleDateFormat
import java.util.Locale

class TutorBookingsFragment : Fragment() {

    private lateinit var recyclerBookings: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: TutorBookingAdapter
    private val bookingsList = mutableListOf<Booking>()

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_bookings, container, false)

        recyclerBookings = view.findViewById(R.id.recyclerBookingsTutor)
        txtEmpty = view.findViewById(R.id.txtEmptyTutorBookings)
        progressBar = view.findViewById(R.id.progressBarTutor)

        recyclerBookings.layoutManager = LinearLayoutManager(requireContext())

        adapter = TutorBookingAdapter(bookingsList) { booking, action ->
            handleBookingAction(booking, action)
        }

        recyclerBookings.adapter = adapter
        fetchBookings()

        return view
    }

    private fun fetchBookings() {
        val tutorId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        txtEmpty.visibility = View.GONE

        db.collection("Bookings")
            .whereEqualTo("TutorId", tutorId)
            .get()
            .addOnSuccessListener { result ->
                bookingsList.clear()

                if (result.isEmpty) {
                    progressBar.visibility = View.GONE
                    txtEmpty.text = "No bookings yet"
                    txtEmpty.visibility = View.VISIBLE
                    recyclerBookings.visibility = View.GONE
                    return@addOnSuccessListener
                }

                for (doc in result) {
                    val studentName = doc.getString("StudentName") ?: "N/A"
                    val studentEmail = doc.getString("StudentEmail") ?: "N/A"
                    val startHourNum = doc.get("Hour") as? Number
                    val endHourNum = doc.get("EndHour") as? Number
                    val startHour = startHourNum?.toInt()?.let { "$it:00" } ?: ""
                    val endHour = endHourNum?.toInt()?.let { "$it:00" } ?: ""
                    val time = if (startHour.isNotEmpty() && endHour.isNotEmpty()) "$startHour - $endHour" else "N/A"
                    val isGroup = doc.getBoolean("IsGroup") ?: false
                    val sessionType = if (isGroup) "Group" else "One-on-One"
                    val date = doc.getTimestamp("BookingDate")?.toDate()?.let { sdf.format(it) } ?: ""
                    val day = doc.getString("Day") ?: ""
                    val status = doc.getString("Status") ?: "Upcoming"
                    val pricePaid = (doc.get("PricePaid") as? Number)?.toDouble() ?: 0.0
                    val isCompleted = doc.getBoolean("IsCompleted") ?: false
                    val isCancelled = doc.getBoolean("IsCancelled") ?: false
                    val loggedAt = doc.getTimestamp("LoggedAt")

                    val booking = Booking(
                        bookingId = doc.id,
                        tutorName = studentName,
                        tutorEmail = studentEmail,
                        startHour = startHour,
                        endHour = endHour,
                        time = time,
                        sessionType = sessionType,
                        date = date,
                        day = day,
                        status = status,
                        pricePaid = pricePaid,
                        isCompleted = isCompleted,
                        isCancelled = isCancelled,
                        loggedAt = loggedAt
                    )
                    bookingsList.add(booking)
                }

                progressBar.visibility = View.GONE
                if (bookingsList.isEmpty()) {
                    txtEmpty.visibility = View.VISIBLE
                    txtEmpty.text = "No bookings yet"
                    recyclerBookings.visibility = View.GONE
                } else {
                    txtEmpty.visibility = View.GONE
                    recyclerBookings.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                txtEmpty.visibility = View.VISIBLE
                txtEmpty.text = "Failed to load bookings"
                Log.e("TutorBookingsFragment", "Failed to load bookings: ${e.message}", e)
            }
    }

    private fun handleBookingAction(booking: Booking, action: String) {
        val bookingsRef = db.collection("Bookings")

        bookingsRef.whereEqualTo("BookingId", booking.bookingId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener

                val doc = snapshot.documents[0]
                val now = Timestamp.now()

                when (action) {
                    "confirm" -> {
                        // Parse startHour and endHour properly
                        val startHour = booking.hour.toDouble()
                        val startMinutes = 0.0  // start hour stored as Int

                        // Parse endHour safely
                        val endParts = booking.endHour.split(":")
                        val endHour = endParts.getOrNull(0)?.toDoubleOrNull() ?: startHour
                        val endMinutes = endParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0

                        // Calculate hours worked as decimal and convert to Int
                        val hoursWorked = (((endHour + endMinutes / 60) - (startHour + startMinutes / 60))
                            .coerceAtLeast(0.0)).toInt()

                        // Prepare update map with exact model field names
                        val updateMap = mapOf(
                            "isCompleted" to true,
                            "loggedAt" to now,
                            "completionTimestamp" to now,
                            "hoursWorked" to hoursWorked,
                            "amountEarned" to (booking.pricePaid ?: 0.0),
                            "status" to "Completed"
                        )

                        // Save to Firestore
                        doc.reference.set(updateMap, SetOptions.merge())
                            .addOnSuccessListener {
                                // Update local object and UI
                                booking.isCompleted = true
                                booking.loggedAt = now
                                booking.completionTimestamp = now
                                booking.hoursWorked = hoursWorked
                                booking.amountEarned = booking.pricePaid
                                booking.status = "Completed"
                                adapter.notifyItemChanged(bookingsList.indexOf(booking))
                            }
                            .addOnFailureListener { e ->
                                Log.e("TutorBookingsFragment", "Failed to mark completed: ${e.message}", e)
                            }
                    }

                    "cancel" -> {
                        val updateMap = mapOf(
                            "status" to "Cancelled",
                            "isCancelled" to true,
                            "updatedAt" to now
                        )
                        doc.reference.set(updateMap, SetOptions.merge())
                            .addOnSuccessListener {
                                booking.status = "Cancelled"
                                booking.isCancelled = true
                                adapter.notifyItemChanged(bookingsList.indexOf(booking))
                            }
                            .addOnFailureListener { e ->
                                Log.e("TutorBookingsFragment", "Failed to cancel booking: ${e.message}", e)
                            }
                    }

                    "chat" -> {
                        // Chat handled in adapter
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("TutorBookingsFragment", "Failed to query booking: ${e.message}", e)
            }
    }



}
