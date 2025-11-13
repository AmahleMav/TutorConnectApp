package com.example.tutorconnect.Views.student

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import Views.student.adapters.SlotAdapter
import com.example.tutorconnect.services.BookingService
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import models.TimeSlot
import java.util.*

class TutorTimeSlotsActivity : AppCompatActivity() {

    private val TAG = "TutorTimeSlotsActivity"

    private lateinit var recyclerSlots: RecyclerView
    private lateinit var progressBar: View
    private lateinit var refreshButton: FloatingActionButton

    private lateinit var spinnerDay: Spinner
    private lateinit var spinnerSessionType: Spinner
    private lateinit var spinnerAvailability: Spinner
    private lateinit var btnClearFilters: MaterialButton

    private val bookingService = BookingService()
    private val auth = FirebaseAuth.getInstance()
    private var slotsListener: ListenerRegistration? = null

    private lateinit var tutorId: String
    private lateinit var tutorName: String
    private var tutorProfile: Map<String, Any>? = null

    private val daysOfWeek = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    private val allSlots = mutableListOf<TimeSlot>()
    private var filteredSlots: List<TimeSlot> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutor_time_slots)

        recyclerSlots = findViewById(R.id.recyclerSlots)
        progressBar = findViewById(R.id.progressBarSlots)
        refreshButton = findViewById(R.id.btnRefreshSlots)
        spinnerDay = findViewById(R.id.spinnerDay)
        spinnerSessionType = findViewById(R.id.spinnerSessionType)
        spinnerAvailability = findViewById(R.id.spinnerAvailability)
        btnClearFilters = findViewById(R.id.btnClearFilters)

        recyclerSlots.layoutManager = LinearLayoutManager(this)

        tutorId = intent.getStringExtra("tutorId")?.trim() ?: ""
        tutorName = intent.getStringExtra("tutorName")?.trim() ?: ""

        if (tutorId.isEmpty()) {
            Toast.makeText(this, "Invalid tutor profile.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        spinnerDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All") + daysOfWeek)
        spinnerSessionType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All", "One-on-One", "Group"))
        spinnerAvailability.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("All", "Available", "Booked"))

        val itemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) { applyFiltersAndRefresh() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        spinnerDay.onItemSelectedListener = itemSelectedListener
        spinnerSessionType.onItemSelectedListener = itemSelectedListener
        spinnerAvailability.onItemSelectedListener = itemSelectedListener

        btnClearFilters.setOnClickListener {
            spinnerDay.setSelection(0)
            spinnerSessionType.setSelection(0)
            spinnerAvailability.setSelection(0)
            applyFiltersAndRefresh()
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
                Log.e(TAG, "Firestore snapshot error: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot == null || snapshot.isEmpty) {
                Toast.makeText(this, "Tutor profile not found.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }

            tutorProfile = snapshot.documents[0].data
            loadSlots()
        }
    }

    private fun loadSlots() {
        if (tutorProfile == null) {
            progressBar.visibility = View.GONE
            return
        }

        val weeklyAvailability = tutorProfile?.get("WeeklyAvailability") as? Map<String, Any> ?: emptyMap()
        val tempSlots = mutableListOf<TimeSlot>()

        for (day in daysOfWeek) {
            val daySlots = weeklyAvailability[day] as? List<Map<String, Any>> ?: emptyList()
            for (slotData in daySlots) {
                val hour = (slotData["Hour"] as? Number)?.toInt() ?: 8
                val isAvailable = slotData["IsAvailable"] as? Boolean ?: false
                val isGroup = slotData["IsGroup"] as? Boolean ?: false
                val maxStudents = (slotData["MaxStudents"] as? Number)?.toInt() ?: 1
                val groupPrice = (slotData["GroupPricePerHour"] as? Number)?.toDouble() ?: 0.0
                val oneOnOnePrice = (slotData["OneOnOnePricePerHour"] as? Number)?.toDouble() ?: 0.0
                val time = String.format("%02d:00 - %02d:00", hour, hour + 1)

                tempSlots.add(
                    TimeSlot(
                        tutorId = tutorId,
                        day = day,
                        time = time,
                        sessionType = if (isGroup) "Group" else "One-on-One",
                        isAvailable = isAvailable,
                        maxStudents = maxStudents,
                        groupPricePerHour = groupPrice,
                        oneOnOnePricePerHour = oneOnOnePrice
                    )
                )
            }
        }

        // Merge consecutive group slots as before
        val mergedSlots = mutableListOf<TimeSlot>()
        val groupedByDay = tempSlots.groupBy { it.day }

        for ((day, slots) in groupedByDay) {
            val sortedSlots = slots.sortedBy { safeParseStartHour(it.time) }
            var i = 0
            while (i < sortedSlots.size) {
                val current = sortedSlots[i]
                if (current.sessionType == "Group") {
                    var startHour = safeParseStartHour(current.time)
                    var endHour = startHour + 1
                    var maxStudents = current.maxStudents
                    var anyAvailable = current.isAvailable

                    var j = i + 1
                    while (j < sortedSlots.size && sortedSlots[j].sessionType == "Group" &&
                        safeParseStartHour(sortedSlots[j].time) == endHour
                    ) {
                        endHour++
                        maxStudents += sortedSlots[j].maxStudents
                        anyAvailable = anyAvailable || sortedSlots[j].isAvailable
                        j++
                    }

                    mergedSlots.add(current.copy(
                        time = String.format("%02d:00 - %02d:00", startHour, endHour),
                        maxStudents = maxStudents,
                        isAvailable = anyAvailable // we will fix availability next
                    ))

                    i = j
                } else {
                    mergedSlots.add(current)
                    i++
                }
            }
        }

        // Dynamically update group slot availability based on existing bookings
        lifecycleScope.launch(Dispatchers.IO) {
            val db = FirebaseFirestore.getInstance()
            val updatedSlots = mergedSlots.map { slot ->
                if (slot.sessionType == "Group") {
                    try {
                        val bookedCount = db.collection("Bookings")
                            .whereEqualTo("TutorId", slot.tutorId)
                            .whereEqualTo("Day", slot.day)
                            .whereEqualTo("Hour", safeParseStartHour(slot.time))
                            .whereEqualTo("IsGroup", true)
                            .whereEqualTo("IsCancelled", false)
                            .get()
                            .await()
                            .size()

                        slot.copy(isAvailable = bookedCount < slot.maxStudents)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to compute group availability", e)
                        slot
                    }
                } else slot
            }

            withContext(Dispatchers.Main) {
                allSlots.clear()
                allSlots.addAll(updatedSlots.sortedWith(compareBy({ daysOfWeek.indexOf(it.day) }, { safeParseStartHour(it.time) })))
                applyFiltersAndRefresh()
            }
        }
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


    private fun applyFiltersAndRefresh() {
        var list = allSlots.toList()

        val selectedDay = spinnerDay.selectedItem as? String
        if (!selectedDay.isNullOrEmpty() && selectedDay != "All") list = list.filter { it.day.equals(selectedDay, true) }

        val selectedSession = spinnerSessionType.selectedItem as? String
        if (!selectedSession.isNullOrEmpty() && selectedSession != "All") list = list.filter { it.sessionType.equals(selectedSession, true) }

        val selectedAvailability = spinnerAvailability.selectedItem as? String
        if (!selectedAvailability.isNullOrEmpty() && selectedAvailability != "All") {
            list = list.filter { slot ->
                when (selectedAvailability) {
                    "Available" -> slot.isAvailable
                    "Booked" -> !slot.isAvailable
                    else -> true
                }
            }
        }

        filteredSlots = list
        recyclerSlots.adapter = SlotAdapter(filteredSlots) { slot ->
            if (slot.isAvailable) bookSlot(slot)
            else Toast.makeText(this, "This slot is not available.", Toast.LENGTH_SHORT).show()
        }

        progressBar.visibility = View.GONE
        recyclerSlots.visibility = if (filteredSlots.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.txtEmptySlots).visibility = if (filteredSlots.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun safeParseStartHour(timeRange: String): Int {
        return try { timeRange.substringBefore(":").toIntOrNull() ?: 0 } catch (e: Exception) { 0 }
    }


    override fun onDestroy() {
        super.onDestroy()
        slotsListener?.remove()
    }
}