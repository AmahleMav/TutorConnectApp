package com.example.tutorconnect.Views.student

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import Views.student.adapters.SlotAdapter
import com.example.tutorconnect.services.BookingResult
import com.example.tutorconnect.services.BookingService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import models.Booking
import models.TimeSlot
import java.util.*

class TutorTimeSlotsActivity : AppCompatActivity() {

    private lateinit var recyclerSlots: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var refreshButton: FloatingActionButton

    private val bookingService = BookingService()
    private val auth = FirebaseAuth.getInstance()
    private var slotsListener: ListenerRegistration? = null

    private lateinit var tutorId: String
    private lateinit var tutorName: String
    private var tutorProfile: Map<String, Any>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutor_time_slots)

        recyclerSlots = findViewById(R.id.recyclerSlots)
        progressBar = findViewById(R.id.progressBarSlots)
        refreshButton = findViewById(R.id.btnRefreshSlots)

        recyclerSlots.layoutManager = LinearLayoutManager(this)

        tutorId = intent.getStringExtra("tutorId")?.trim() ?: ""
        tutorName = intent.getStringExtra("tutorName")?.trim() ?: ""

        if (tutorId.isEmpty()) {
            Toast.makeText(this, "Invalid tutor profile.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadTutorProfile()
        refreshButton.setOnClickListener { loadSlots() }
    }

    private fun loadTutorProfile() {
        progressBar.visibility = View.VISIBLE
        recyclerSlots.visibility = View.GONE

        val tutorRef = FirebaseFirestore.getInstance().collection("TutorProfiles").whereEqualTo("UserId", tutorId)

        slotsListener?.remove()
        slotsListener = tutorRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Toast.makeText(this, "Error loading tutor profile.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }

            if (snapshot == null || snapshot.isEmpty) {
                Toast.makeText(this, "Tutor profile not found.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }

            val doc = snapshot.documents[0]
            tutorProfile = doc.data
            loadSlots()
        }
    }

    private fun loadSlots() {
        if (tutorProfile == null) return

        val weeklyAvailability = tutorProfile?.get("WeeklyAvailability") as? Map<String, Any> ?: emptyMap()
        val daysOfWeek = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
        val slots = mutableListOf<TimeSlot>()

        for (day in daysOfWeek) {
            val daySlots = weeklyAvailability[day] as? List<Map<String, Any>> ?: emptyList()
            for (slotData in daySlots) {
                val hour = (slotData["Hour"] as? Number)?.toInt() ?: 8
                val isAvailable = slotData["IsAvailable"] as? Boolean ?: false
                val isGroup = slotData["IsGroup"] as? Boolean ?: false
                val groupPrice = (slotData["GroupPricePerHour"] as? Number)?.toDouble() ?: 0.0
                val oneOnOnePrice = (slotData["OneOnOnePricePerHour"] as? Number)?.toDouble() ?: 0.0
                val time = String.format("%02d:00 - %02d:00", hour, hour + 1)

                slots.add(
                    TimeSlot(
                        tutorId = tutorId,
                        day = day,
                        time = time,
                        sessionType = if (isGroup) "Group" else "One-on-One",
                        isAvailable = isAvailable,
                        maxStudents = (slotData["MaxStudents"] as? Number)?.toInt() ?: 1,
                        groupPricePerHour = groupPrice,
                        oneOnOnePricePerHour = oneOnOnePrice
                    )
                )
            }
        }

        slots.sortWith(compareBy({ daysOfWeek.indexOf(it.day) }, { it.time.substringBefore(":").toInt() }))

        recyclerSlots.adapter = SlotAdapter(slots) { slot ->
            if (slot.isAvailable) bookSlot(slot)
            else Toast.makeText(this, "This slot is not available.", Toast.LENGTH_SHORT).show()
        }

        progressBar.visibility = View.GONE
        recyclerSlots.visibility = if (slots.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun bookSlot(slot: TimeSlot) {
        val studentId = auth.currentUser?.uid
        if (studentId.isNullOrEmpty()) {
            Toast.makeText(this, "You must be logged in to book a session.", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Book $tutorName")
        val datePicker = android.widget.DatePicker(this)
        builder.setView(datePicker)

        val dayMap = mapOf(
            "Monday" to Calendar.MONDAY,
            "Tuesday" to Calendar.TUESDAY,
            "Wednesday" to Calendar.WEDNESDAY,
            "Thursday" to Calendar.THURSDAY,
            "Friday" to Calendar.FRIDAY,
            "Saturday" to Calendar.SATURDAY,
            "Sunday" to Calendar.SUNDAY
        )
        val allowedDay = dayMap[slot.day] ?: Calendar.MONDAY
        val toast = Toast.makeText(this, "Selected date must be a ${slot.day}.", Toast.LENGTH_SHORT)

        builder.setPositiveButton("Confirm") { dialog, _ ->
            val selectedCal = Calendar.getInstance()
            selectedCal.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 2, 0, 0)

            if (selectedCal.get(Calendar.DAY_OF_WEEK) != allowedDay) {
                toast.show()
                return@setPositiveButton
            }

            // Parse start and end hours safely
            val startHour = slot.time.substringBefore(":").toIntOrNull() ?: 8
            val endHourStr = slot.time.substringAfter("-").trim()
            val endHour = endHourStr.substringBefore(":").toIntOrNull() ?: startHour + 1

            // Calculate hours worked
            val hoursWorked = (endHour - startHour).coerceAtLeast(0)

            lifecycleScope.launch {
                try {
                    val db = FirebaseFirestore.getInstance()

                    // Fetch hourly rates
                    val profileSnapshot = db.collection("TutorProfiles")
                        .whereEqualTo("UserId", slot.tutorId)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()

                    val oneOnOneRate = profileSnapshot?.getDouble("OneOnOneHourlyRate") ?: slot.oneOnOnePricePerHour
                    val groupRate = profileSnapshot?.getDouble("GroupHourlyRate") ?: slot.groupPricePerHour
                    val pricePaid = if (slot.sessionType.equals("Group", true)) groupRate else oneOnOneRate

                    val booking = hashMapOf(
                        "BookingId" to UUID.randomUUID().toString(),
                        "TutorId" to slot.tutorId,
                        "StudentId" to studentId,
                        "StudentName" to null,
                        "TutorName" to tutorName,
                        "Day" to slot.day,
                        "Hour" to startHour,
                        "EndHour" to endHour,
                        "HoursWorked" to hoursWorked,        // <-- calculated automatically
                        "IsGroup" to slot.sessionType.equals("Group", true),
                        "IsCancelled" to false,
                        "IsCompleted" to false,
                        "PricePaid" to pricePaid,
                        "AmountEarned" to pricePaid,
                        "BookingDate" to com.google.firebase.Timestamp(selectedCal.time),
                        "LoggedAt" to null,
                        "Rating" to null,
                        "Review" to null,
                        "Timestamp" to com.google.firebase.Timestamp.now()
                    )

                    progressBar.visibility = View.VISIBLE
                    recyclerSlots.visibility = View.GONE

                    db.collection("Bookings").add(booking).await()
                    Toast.makeText(this@TutorTimeSlotsActivity, "Booking confirmed!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@TutorTimeSlotsActivity, StudentDashboard::class.java).apply {
                        putExtra("navigateTo", "bookings")
                    })
                    finish()

                } catch (e: Exception) {
                    Toast.makeText(this@TutorTimeSlotsActivity, "Booking failed: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    progressBar.visibility = View.GONE
                    recyclerSlots.visibility = View.VISIBLE
                }
            }

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }




    override fun onDestroy() {
        super.onDestroy()
        slotsListener?.remove()
    }
}
