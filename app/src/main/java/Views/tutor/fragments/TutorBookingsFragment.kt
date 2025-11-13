package Views.tutor.fragments

import android.content.Intent
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
import Views.tutor.adapters.TutorBookingAdapter
import Views.shared.ChatDetailActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import models.Booking
import java.text.SimpleDateFormat
import java.util.Locale

class TutorBookingsFragment : Fragment() {

    private lateinit var recyclerBookings: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabUpcoming: TextView
    private lateinit var tabPast: TextView

    private lateinit var adapter: TutorBookingAdapter
    private val allBookings = mutableListOf<Booking>()
    private val bookingsList = mutableListOf<Booking>()
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // cached tutor name (fetched once)
    private var cachedTutorFullName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_bookings, container, false)

        recyclerBookings = view.findViewById(R.id.recyclerBookings)
        txtEmpty = view.findViewById(R.id.txtEmptyBookings)
        progressBar = view.findViewById(R.id.progressBar)
        tabUpcoming = view.findViewById(R.id.tabUpcoming)
        tabPast = view.findViewById(R.id.tabPast)

        recyclerBookings.layoutManager = LinearLayoutManager(requireContext())
        adapter = TutorBookingAdapter(bookingsList) { booking, action ->
            when (action) {
                "chat" -> {
                    Log.d("TutorBookingsFragment", "Chat clicked for ${booking.studentName} (id=${booking.studentId})")
                    createOrOpenChat(booking)
                }
            }
        }
        recyclerBookings.adapter = adapter

        tabUpcoming.setOnClickListener { showUpcoming() }
        tabPast.setOnClickListener { showPast() }

        // Try to quickly populate cached tutor name from auth displayName, fallback to DB
        cachedTutorFullName = auth.currentUser?.displayName
        if (cachedTutorFullName.isNullOrBlank()) {
            fetchTutorNameAsync()
        }

        fetchBookings()
        return view
    }

    private fun fetchTutorNameAsync() {
        val tutorId = auth.currentUser?.uid ?: return
        db.collection("Tutors").document(tutorId).get()
            .addOnSuccessListener { doc ->
                val first = doc.getString("Name") ?: ""
                val last = doc.getString("Surname") ?: ""
                val full = listOf(first, last).joinToString(" ").trim()
                if (full.isNotBlank()) cachedTutorFullName = full
            }
            .addOnFailureListener { e ->
                Log.w("TutorBookingsFragment", "Could not fetch tutor name: ${e.message}")
            }
    }

    private fun fetchBookings() {
        val tutorId = auth.currentUser?.uid ?: return
        progressBar.visibility = View.VISIBLE
        txtEmpty.visibility = View.GONE

        db.collection("Bookings")
            .whereEqualTo("TutorId", tutorId)
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
                    val studentId = doc.getString("StudentId") ?: doc.getString("studentId") ?: ""
                    val startHourNum = doc.get("Hour") as? Number
                    val endHourNum = doc.get("EndHour") as? Number
                    val startHour = startHourNum?.toInt()?.let { "$it:00" } ?: ""
                    val endHour = endHourNum?.toInt()?.let { "$it:00" } ?: ""
                    val time = if (startHour.isNotEmpty() && endHour.isNotEmpty()) "$startHour - $endHour" else "N/A"

                    val isGroup = doc.getBoolean("IsGroup") ?: false
                    val sessionType = if (isGroup) "Group" else "One-on-One"
                    val date = doc.getTimestamp("BookingDate")?.toDate()?.let { sdf.format(it) } ?: ""
                    val day = doc.getString("Day") ?: ""
                    val status = doc.getString("Status") ?: doc.getString("status") ?: "Upcoming"
                    val pricePaid = (doc.get("PricePaid") as? Number)?.toDouble() ?: 0.0
                    val isCompleted = doc.getBoolean("IsCompleted") ?: false
                    val isCancelled = doc.getBoolean("IsCancelled") ?: false

                    if (studentId.isBlank()) {
                        // add placeholder booking (keeps list consistent); no chat for this entry
                        val booking = Booking(
                            bookingId = doc.id,
                            studentId = "",
                            studentName = "Unknown Student",
                            studentEmail = "",
                            startHour = startHour,
                            endHour = endHour,
                            time = time,
                            sessionType = sessionType,
                            date = date,
                            day = day,
                            status = status,
                            pricePaid = pricePaid,
                            isCompleted = isCompleted,
                            isCancelled = isCancelled
                        )
                        allBookings.add(booking)
                        processedCount++
                        if (processedCount == bookingDocs.size) {
                            showUpcoming()
                            progressBar.visibility = View.GONE
                        }
                        continue
                    }

                    // Get student details
                    db.collection("Students").document(studentId).get()
                        .addOnSuccessListener { studentDoc ->
                            val studentName = studentDoc.getString("Name") ?: "N/A"
                            val studentSurname = studentDoc.getString("Surname") ?: ""
                            val studentFullName = "$studentName $studentSurname".trim()
                            val studentEmail = studentDoc.getString("Email") ?: "N/A"

                            val booking = Booking(
                                bookingId = doc.id,
                                studentId = studentId,
                                studentName = studentFullName,
                                studentEmail = studentEmail,
                                startHour = startHour,
                                endHour = endHour,
                                time = time,
                                sessionType = sessionType,
                                date = date,
                                day = day,
                                status = status,
                                pricePaid = pricePaid,
                                isCompleted = isCompleted,
                                isCancelled = isCancelled
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
                            Log.e("TutorBookingsFragment", "Student fetch failed: ${e.message}", e)
                            if (processedCount == bookingDocs.size) {
                                showUpcoming()
                                progressBar.visibility = View.GONE
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("TutorBookingsFragment", "Failed: ${e.message}", e)
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

    /**
     * Replace previous createOrOpenChat: look for existing Chats doc with TutorId + StudentId first,
     * otherwise create a new chat doc with the same shape TutorInbox expects, then open ChatDetailActivity.
     */
    private fun createOrOpenChat(booking: Booking) {
        val tutorId = auth.currentUser?.uid ?: run {
            Log.w("TutorBookingsFragment", "No tutor logged in")
            return
        }
        val studentId = booking.studentId ?: run {
            Log.w("TutorBookingsFragment", "Booking ${booking.bookingId} has no studentId — can't open chat")
            return
        }

        progressBar.visibility = View.VISIBLE

        // Query for existing chat with exact TutorId + StudentId
        db.collection("Chats")
            .whereEqualTo("TutorId", tutorId)
            .whereEqualTo("StudentId", studentId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    // Found existing chat — reuse it
                    val doc = snapshot.documents[0]
                    val chatId = doc.id
                    Log.d("TutorBookingsFragment", "Found existing chat $chatId for tutor=$tutorId student=$studentId")
                    progressBar.visibility = View.GONE
                    openChatActivity(chatId, booking)
                    return@addOnSuccessListener
                }

                // No existing chat found — create a new chat doc following TutorInbox structure
                val newChatRef = db.collection("Chats").document()
                val chatId = newChatRef.id
                val tutorName = cachedTutorFullName ?: auth.currentUser?.displayName ?: "Tutor"
                val studentName = booking.studentName ?: booking.studentEmail ?: "Student"

                val chatData = hashMapOf(
                    "StudentId" to studentId,
                    "TutorId" to tutorId,
                    "Messages" to listOf<Map<String, Any>>(), // empty array
                    "CreatedAt" to com.google.firebase.Timestamp.now(),
                    "LastReadByStudent" to null,
                    "UnreadBy" to listOf(studentId) // student has unread by default
                )

                newChatRef.set(chatData)
                    .addOnSuccessListener {
                        Log.d("TutorBookingsFragment", "Created new chat $chatId for tutor=$tutorId student=$studentId")
                        progressBar.visibility = View.GONE
                        openChatActivity(chatId, booking)
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Log.e("TutorBookingsFragment", "Failed to create chat doc: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Log.e("TutorBookingsFragment", "Failed to query chats: ${e.message}", e)
            }
    }

    /**
     * Start ChatDetailActivity with extras matching what ChatDetailActivity expects.
     */
    private fun openChatActivity(chatId: String, booking: Booking) {
        val tutorId = auth.currentUser?.uid ?: ""
        val tutorName = cachedTutorFullName ?: auth.currentUser?.displayName ?: ""
        val studentId = booking.studentId ?: ""
        val studentName = booking.studentName ?: booking.studentEmail ?: "Student"

        val intent = Intent(requireContext(), ChatDetailActivity::class.java).apply {
            putExtra("ChatId", chatId)
            putExtra("StudentId", studentId)
            putExtra("TutorId", tutorId)
            putExtra("studentName", studentName)
            putExtra("tutorName", tutorName)
        }
        startActivity(intent)
    }
}
