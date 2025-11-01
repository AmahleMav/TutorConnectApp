package Views.tutor

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tutorconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class AvailabilityActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private lateinit var layoutContainer: LinearLayout
    private lateinit var mergedContainer: LinearLayout
    private lateinit var btnSaveAvailability: Button
    private lateinit var btnBack: ImageButton
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "AvailabilityActivity"

    private val daysOfWeek = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    private val daySlotMap = mutableMapOf<String, MutableList<LinearLayout>>()

    private var currentProfileDocId: String? = null

    data class TimeSlot(
        val Hour: Int = 0,
        var IsAvailable: Boolean = false,
        var IsGroup: Boolean = false,
        var MaxStudents: Int = 1,
        var OneOnOnePricePerHour: Double = 0.0,
        var GroupPricePerHour: Double = 0.0
    )

    data class MergedSession(
        var TutorId: String = "",
        var Day: String = "",
        var StartHour: Int = 0,
        var EndHour: Int = 0,
        var IsGroup: Boolean = false,
        var MaxStudents: Int = 1,
        var GroupPricePerHour: Double = 0.0,
        var HourlyRate: Double = 0.0,
        var RegisteredStudents: Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_availability)

        layoutContainer = findViewById(R.id.layoutContainer)
        mergedContainer = findViewById(R.id.mergedContainer)
        btnSaveAvailability = findViewById(R.id.btnSaveAvailability)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        generateAvailabilityLayout()
        loadAndRenderAvailability()

        btnSaveAvailability.setOnClickListener { saveAvailability() }
    }

    private fun generateAvailabilityLayout() {
        layoutContainer.removeAllViews()
        daySlotMap.clear()

        for (day in daysOfWeek) {
            val dayHeader = TextView(this).apply {
                text = "▶ $day"
                textSize = 18f
                setPadding(0,16,0,8)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(android.R.color.black, theme))
            }

            val slotsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = LinearLayout.GONE
            }

            val dailySlots = mutableListOf<LinearLayout>()
            for (hour in 8..17) {
                val slotView = layoutInflater.inflate(R.layout.item_availability_slot, layoutContainer, false) as LinearLayout
                val hourLabel = slotView.findViewById<TextView>(R.id.txtHour)
                val availableSwitch = slotView.findViewById<Switch>(R.id.switchAvailable)
                val groupCheckBox = slotView.findViewById<CheckBox>(R.id.checkGroup)
                val groupSizeInput = slotView.findViewById<EditText>(R.id.etGroupSize)

                hourLabel.text = String.format("%02d:00 - %02d:00", hour, hour+1)
                groupSizeInput.isEnabled = false

                groupCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    groupSizeInput.isEnabled = isChecked
                    if (!isChecked) groupSizeInput.text.clear()
                    if (isChecked) availableSwitch.isChecked = false
                    updateSlotColor(slotView, availableSwitch.isChecked, isChecked)
                }

                availableSwitch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) groupCheckBox.isChecked = false
                    updateSlotColor(slotView, isChecked, groupCheckBox.isChecked)
                }

                slotsContainer.addView(slotView)
                dailySlots.add(slotView)
            }

            dayHeader.setOnClickListener {
                if (slotsContainer.visibility == LinearLayout.GONE) {
                    slotsContainer.visibility = LinearLayout.VISIBLE
                    dayHeader.text = "▼ $day"
                } else {
                    slotsContainer.visibility = LinearLayout.GONE
                    dayHeader.text = "▶ $day"
                }
            }

            layoutContainer.addView(dayHeader)
            layoutContainer.addView(slotsContainer)
            daySlotMap[day] = dailySlots
        }
    }

    private fun updateSlotColor(slotView: LinearLayout, available: Boolean, isGroup: Boolean) {
        val color = when {
            isGroup -> R.color.green_light
            available -> R.color.blue_light
            else -> R.color.red_light
        }
        slotView.setBackgroundColor(ContextCompat.getColor(this, color))
    }

    private fun loadAndRenderAvailability() {
        val tutorId = auth.currentUser?.uid ?: return
        launch {
            try {
                val snap = db.collection("TutorProfiles")
                    .whereEqualTo("UserId", tutorId)
                    .get()
                    .await()

                if (snap.isEmpty) {
                    renderMerged(emptyMap())
                    return@launch
                }

                val doc = snap.documents[0]
                val rawWeekly = doc.get("WeeklyAvailability") as? Map<String, Any>

                val weeklyMap = mutableMapOf<String, MutableList<TimeSlot>>()
                rawWeekly?.forEach { (day, anyList) ->
                    val slotsList = mutableListOf<TimeSlot>()
                    val asList = anyList as? List<*> ?: emptyList<Any>()
                    for (raw in asList) {
                        val m = raw as? Map<*, *> ?: continue
                        val hour = (m["Hour"] as? Number)?.toInt() ?: continue
                        val isAv = (m["IsAvailable"] as? Boolean) ?: false
                        val isGroup = (m["IsGroup"] as? Boolean) ?: false
                        val max = (m["MaxStudents"] as? Number)?.toInt() ?: 1
                        val oneOn = (m["OneOnOnePricePerHour"] as? Number)?.toDouble() ?: 0.0
                        val groupP = (m["GroupPricePerHour"] as? Number)?.toDouble() ?: 0.0
                        slotsList.add(TimeSlot(hour, isAv, isGroup, max, oneOn, groupP))
                    }
                    weeklyMap[day] = slotsList
                }

                withContext(Dispatchers.Main) { populatePerHourGrid(weeklyMap) }
                val merged = mergeAvailability(weeklyMap, tutorId)
                withContext(Dispatchers.Main) { renderMerged(merged) }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading availability", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AvailabilityActivity, "Loaded availability", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populatePerHourGrid(weeklyMap: Map<String, List<TimeSlot>>) {
        for ((day, layouts) in daySlotMap) {
            val slotsForDay = weeklyMap[day] ?: emptyList()
            val byHour = slotsForDay.associateBy { it.Hour }
            for ((index, layout) in layouts.withIndex()) {
                val hourText = layout.findViewById<TextView>(R.id.txtHour).text.toString()
                val hour = hourText.substring(0,2).toInt()
                val availableSwitch = layout.findViewById<Switch>(R.id.switchAvailable)
                val groupCheckBox = layout.findViewById<CheckBox>(R.id.checkGroup)
                val groupSizeInput = layout.findViewById<EditText>(R.id.etGroupSize)

                val slot = byHour[hour]
                if (slot != null) {
                    availableSwitch.isChecked = slot.IsAvailable
                    groupCheckBox.isChecked = slot.IsGroup
                    groupSizeInput.setText(slot.MaxStudents.toString())
                    groupSizeInput.isEnabled = slot.IsGroup
                } else {
                    availableSwitch.isChecked = false
                    groupCheckBox.isChecked = false
                    groupSizeInput.setText("1")
                    groupSizeInput.isEnabled = false
                }
                updateSlotColor(layout, availableSwitch.isChecked, groupCheckBox.isChecked)
            }
        }
    }

    private fun mergeAvailability(
        weekly: Map<String, List<TimeSlot>>,
        tutorId: String
    ): Map<String, List<MergedSession>> {
        val result = mutableMapOf<String, MutableList<MergedSession>>()
        for ((day, slots) in weekly) {
            val filtered = slots.filter { it.IsAvailable || it.IsGroup }.sortedBy { it.Hour }
            if (filtered.isEmpty()) continue
            val mergedList = mutableListOf<MergedSession>()
            var i = 0
            while (i < filtered.size) {
                val cur = filtered[i]
                var start = cur.Hour
                var end = start + 1
                if (cur.IsGroup) {
                    var j = i + 1
                    while (j < filtered.size && filtered[j].IsGroup && filtered[j].Hour == end) {
                        end++; j++
                    }
                    mergedList.add(
                        MergedSession(
                            TutorId = tutorId,
                            Day = day,
                            StartHour = start,
                            EndHour = end,
                            IsGroup = true,
                            MaxStudents = cur.MaxStudents,
                            GroupPricePerHour = if (cur.GroupPricePerHour > 0) cur.GroupPricePerHour else cur.OneOnOnePricePerHour,
                            HourlyRate = if (cur.GroupPricePerHour > 0) cur.GroupPricePerHour else cur.OneOnOnePricePerHour
                        )
                    )
                    i = j
                } else {
                    mergedList.add(
                        MergedSession(
                            TutorId = tutorId,
                            Day = day,
                            StartHour = start,
                            EndHour = end,
                            IsGroup = false,
                            MaxStudents = 1,
                            HourlyRate = cur.OneOnOnePricePerHour
                        )
                    )
                    i++
                }
            }
            if (mergedList.isNotEmpty()) result[day] = mergedList
        }
        return result
    }

    private fun renderMerged(merged: Map<String, List<MergedSession>>) {
        mergedContainer.removeAllViews()
        if (merged.isEmpty()) {
            val t = TextView(this).apply { text = "No merged sessions saved yet."; setPadding(16,16,16,16) }
            mergedContainer.addView(t)
            return
        }
        for ((day, sessions) in merged) {
            val header = TextView(this).apply {
                text = day
                textSize = 18f
                setPadding(8,12,8,6)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            mergedContainer.addView(header)
            for (s in sessions) {
                val tv = TextView(this).apply {
                    text = buildString {
                        append("${s.StartHour}:00 - ${s.EndHour}:00")
                        append("  |  ${if (s.IsGroup) "Group" else "One-on-one"}")
                        append("  |  Rate: R${"%.0f".format(s.HourlyRate)}")
                        if (s.IsGroup) append("  |  Spots: ${s.MaxStudents - s.RegisteredStudents}")
                    }
                    setPadding(10,8,10,8)
                    setBackgroundResource(R.drawable.item_background)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(6,6,6,6)
                    }
                }
                mergedContainer.addView(tv)
            }
        }
    }

    private fun saveAvailability() {
        val tutorId = auth.currentUser?.uid ?: return
        val availabilityData = mutableMapOf<String, Any>()

        // Build weekly availability
        for ((day, slots) in daySlotMap) {
            val daySlots = mutableListOf<Map<String, Any>>()
            for (slotLayout in slots) {
                val hourLabel = slotLayout.findViewById<TextView>(R.id.txtHour)
                val availableSwitch = slotLayout.findViewById<Switch>(R.id.switchAvailable)
                val groupCheckBox = slotLayout.findViewById<CheckBox>(R.id.checkGroup)
                val groupSizeInput = slotLayout.findViewById<EditText>(R.id.etGroupSize)
                val hour = hourLabel.text.substring(0, 2).toInt()

                daySlots.add(
                    mapOf(
                        "Hour" to hour.toLong(),
                        "IsAvailable" to availableSwitch.isChecked,
                        "IsGroup" to groupCheckBox.isChecked,
                        "MaxStudents" to (groupSizeInput.text.toString().toIntOrNull() ?: 1).toLong(),
                        "OneOnOnePricePerHour" to 550L,
                        "GroupPricePerHour" to 0L
                    )
                )
            }
            availabilityData[day] = daySlots
        }

        launch {
            try {
                // Determine the document to update or create
                val docRef = if (currentProfileDocId != null) {
                    db.collection("TutorProfiles").document(currentProfileDocId!!)
                } else {
                    // Check if a profile already exists
                    val snap = db.collection("TutorProfiles")
                        .whereEqualTo("UserId", tutorId)
                        .get()
                        .await()

                    if (!snap.isEmpty) {
                        currentProfileDocId = snap.documents[0].id
                        db.collection("TutorProfiles").document(currentProfileDocId!!)
                    } else {
                        // Create new document with tutorId as ID
                        val newDoc = db.collection("TutorProfiles").document(tutorId)
                        currentProfileDocId = newDoc.id
                        newDoc
                    }
                }

                // Save / merge the availability data
                docRef.set(
                    mapOf(
                        "UserId" to tutorId,
                        "WeeklyAvailability" to availabilityData
                    ),
                    SetOptions.merge()
                ).await()  // <- await ensures this is synchronous in the coroutine

                // Prepare data for UI update
                val weeklyMap = availabilityData.mapValues { entry ->
                    (entry.value as List<Map<String, Any>>).map { m ->
                        TimeSlot(
                            Hour = (m["Hour"] as? Number)?.toInt() ?: 0,
                            IsAvailable = m["IsAvailable"] as? Boolean ?: false,
                            IsGroup = m["IsGroup"] as? Boolean ?: false,
                            MaxStudents = (m["MaxStudents"] as? Number)?.toInt() ?: 1
                        )
                    }
                }

                // Update UI on the main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AvailabilityActivity, "Availability saved successfully!", Toast.LENGTH_SHORT).show()
                    populatePerHourGrid(weeklyMap)
                    renderMerged(mergeAvailability(weeklyMap, tutorId))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save availability", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AvailabilityActivity, "Failed to save availability.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
