package Views.tutor

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import models.Booking

class HoursLogActivity : AppCompatActivity() {

    private val TAG = "HoursLogActivity"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var toolbar: Toolbar
    private lateinit var txtTotalHours: TextView
    private lateinit var txtTotalEarnings: TextView
    private lateinit var txtAvgRating: TextView
    private lateinit var txtReviewsCount: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: Views.tutor.adapters.HoursLogAdapter
    private lateinit var tvEmpty: TextView

    private val rows = mutableListOf<Booking>()

    // Cache: StudentId -> "Name Surname"
    private val studentNameCache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hours_log)

        // Toolbar setup: only show "Hours & Reviews"
        toolbar = findViewById(R.id.toolbarHoursLog)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Hours & Reviews"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Views
        txtTotalHours = findViewById(R.id.txtTotalHours)
        txtTotalEarnings = findViewById(R.id.txtTotalEarnings)
        txtAvgRating = findViewById(R.id.txtAvgRating)
        txtReviewsCount = findViewById(R.id.txtReviewsCount)
        recycler = findViewById(R.id.recyclerHoursLog)
        tvEmpty = findViewById(R.id.tvHoursEmptyState)

        adapter = Views.tutor.adapters.HoursLogAdapter(rows) { booking ->
            Toast.makeText(this, "${booking.studentName.ifBlank { "Student" }} — ${booking.date}", Toast.LENGTH_SHORT).show()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadCompletedSessions()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Resolve student's full name using StudentId.
     * - Tries document lookup Users/{studentId}
     * - If not found, queries Users where UserId == studentId
     * - Tries multiple candidate field names for first/last or display/full name
     * - Caches result in studentNameCache
     */
    private suspend fun resolveStudentFullName(studentId: String): String {
        // return cached if present
        studentNameCache[studentId]?.let { return it }

        if (studentId.isBlank()) return "Student"

        return try {
            // 1) Try document lookup
            var userDoc: DocumentSnapshot = db.collection("Users").document(studentId).get().await()

            // 2) If doc doesn't exist, try query where UserId == studentId
            if (!userDoc.exists()) {
                val q = db.collection("Users").whereEqualTo("UserId", studentId).limit(1).get().await()
                if (q.documents.isNotEmpty()) userDoc = q.documents[0]
            }

            if (!userDoc.exists()) {
                android.util.Log.w(TAG, "Users lookup: no user found for id/value '$studentId'")
                "Student"
            } else {
                // candidate keys
                val firstCandidates = listOf("Name", "name", "FirstName", "firstName", "first_name", "first")
                val lastCandidates = listOf("Surname", "surname", "LastName", "lastName", "last_name", "last")
                val displayCandidates = listOf("displayName", "fullName", "full_name", "fullname", "display_name")

                var first = ""
                var last = ""
                for (k in firstCandidates) {
                    val v = userDoc.getString(k)
                    if (!v.isNullOrBlank()) { first = v.trim(); break }
                }
                for (k in lastCandidates) {
                    val v = userDoc.getString(k)
                    if (!v.isNullOrBlank()) { last = v.trim(); break }
                }

                // if both blank, try display/full name keys
                if (first.isBlank() && last.isBlank()) {
                    for (k in displayCandidates) {
                        val v = userDoc.getString(k)
                        if (!v.isNullOrBlank()) {
                            val combined = v.trim()
                            studentNameCache[studentId] = combined
                            return combined
                        }
                    }
                }

                val combined = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { "Student" }
                studentNameCache[studentId] = combined
                combined
            }
        } catch (ex: Exception) {
            android.util.Log.w(TAG, "Failed to resolve user for '$studentId': ${ex.message}")
            "Student"
        }
    }

    /**
     * Load only completed sessions and resolve student names via StudentId (with caching).
     */
    private fun loadCompletedSessions() {
        val passedTutorId = intent.getStringExtra("tutorId")
        val tutorId = passedTutorId ?: auth.currentUser?.uid

        if (tutorId.isNullOrBlank()) {
            txtTotalHours.text = "0.0"
            txtTotalEarnings.text = "R0.00"
            txtAvgRating.text = "—"
            txtReviewsCount.text = "0"
            showEmpty(true, "No tutor found.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val snapshot = db.collection("Bookings")
                    .whereEqualTo("TutorId", tutorId)
                    .whereEqualTo("IsCompleted", true)
                    .get().await()

                val tmp = mutableListOf<Booking>()
                var totalHours = 0.0
                var totalEarnings = 0.0
                var ratingSum = 0.0
                var ratingCount = 0

                // Collect unique studentIds first to minimize repeated calls (optional improvement)
                val studentIds = snapshot.documents.mapNotNull { it.getString("StudentId") }.toSet()

                // Pre-warm cache by batch resolving unique ids (parallel-ish)
                for (sid in studentIds) {
                    if (!studentNameCache.containsKey(sid) && sid.isNotBlank()) {
                        try {
                            val resolved = resolveStudentFullName(sid)
                            studentNameCache[sid] = resolved
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "Pre-resolve failed for $sid: ${e.message}")
                        }
                    }
                }

                for (doc in snapshot.documents) {
                    try {
                        val bookingId = doc.id
                        val studentId = doc.getString("StudentId") ?: ""

                        // Use cached or resolve if missing
                        val fullStudentName = if (studentId.isNotBlank()) {
                            studentNameCache[studentId] ?: resolveStudentFullName(studentId)
                        } else {
                            "Student"
                        }

                        val studentEmail = doc.getString("StudentEmail") ?: ""
                        val tutorName = doc.getString("TutorName") ?: ""
                        val tutorEmail = doc.getString("TutorEmail") ?: ""

                        val bookingDateTs = doc.get("BookingDate") as? Timestamp
                        val dateStr = bookingDateTs?.toDate()?.let {
                            android.text.format.DateFormat.format("dd/MM/yyyy", it).toString()
                        } ?: (doc.getString("Date") ?: "")

                        val startHourNum = doc.get("Hour") as? Number
                        val endHourNum = doc.get("EndHour") as? Number
                        val startHour = startHourNum?.toInt()?.let { "%02d:00".format(it) } ?: ""
                        val endHour = endHourNum?.toInt()?.let { "%02d:00".format(it) } ?: ""
                        val time =
                            if (startHour.isNotEmpty() && endHour.isNotEmpty()) "$startHour - $endHour"
                            else (doc.getString("Time") ?: "")

                        val hoursWorked = (doc.get("HoursWorked") as? Number)?.toDouble() ?: 0.0
                        val isGroup = doc.getBoolean("IsGroup") ?: false

                        // Use PricePaid for earnings/totals
                        val pricePaid = (doc.get("PricePaid") as? Number)?.toDouble()
                            ?: (doc.get("AmountEarned") as? Number)?.toDouble()
                            ?: 0.0

                        val ratingVal = (doc.get("Rating") as? Number)?.toDouble()
                        val reviewText = doc.getString("Review") ?: doc.getString("Comment") ?: ""

                        val booking = Booking(
                            bookingId = bookingId,
                            tutorId = tutorId,
                            tutorName = tutorName,
                            tutorEmail = tutorEmail,
                            studentId = studentId,
                            studentName = fullStudentName, // resolved via StudentId
                            studentEmail = studentEmail,
                            day = doc.getString("Day") ?: "",
                            date = dateStr,
                            startHour = startHour,
                            endHour = endHour,
                            time = time,
                            sessionType = if (isGroup) "Group session" else "One-on-one",
                            isGroup = isGroup,
                            isCancelled = doc.getBoolean("IsCancelled") ?: false,
                            isCompleted = true,
                            status = "Completed",
                            hour = (doc.get("Hour") as? Number)?.toInt() ?: 0,
                            amountEarned = (doc.get("AmountEarned") as? Number)?.toDouble(),
                            pricePaid = pricePaid,
                            bookingDate = doc.get("BookingDate") as? Timestamp,
                            loggedAt = doc.get("LoggedAt") as? Timestamp,
                            hoursWorked = hoursWorked,
                            rating = ratingVal,
                            comment = if (reviewText.isBlank()) null else reviewText,
                            isRated = doc.getBoolean("IsRated") ?: false,
                            studentAttended = doc.getBoolean("StudentAttended") ?: false,
                            attendanceTimestamp = doc.get("AttendanceTimestamp") as? Timestamp,
                            completionTimestamp = doc.get("CompletionTimestamp") as? Timestamp,
                            updatedAt = doc.get("UpdatedAt") as? Timestamp,
                            createdAt = doc.get("CreatedAt") as? Timestamp
                        )

                        tmp.add(booking)
                        totalHours += hoursWorked
                        totalEarnings += pricePaid
                        if (ratingVal != null) {
                            ratingSum += ratingVal
                            ratingCount++
                        }
                    } catch (ex: Exception) {
                        android.util.Log.w(TAG, "Error parsing booking ${doc.id}: ${ex.message}")
                    }
                }

                val avgRating = if (ratingCount > 0) ratingSum / ratingCount else null

                withContext(Dispatchers.Main) {
                    rows.clear()
                    rows.addAll(tmp)
                    adapter.notifyDataSetChanged()

                    txtTotalHours.text = String.format("%.1f", totalHours)
                    txtTotalEarnings.text = String.format("R%.2f", totalEarnings)
                    txtAvgRating.text = avgRating?.let { String.format("%.2f", it) } ?: "—"
                    txtReviewsCount.text = ratingCount.toString()

                    if (rows.isEmpty()) showEmpty(true, "No completed sessions yet.")
                    else showEmpty(false, null)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed loading completed sessions: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    txtTotalHours.text = "0.0"
                    txtTotalEarnings.text = "R0.00"
                    txtAvgRating.text = "—"
                    txtReviewsCount.text = "0"
                    showEmpty(true, "Failed to load data.")
                    Toast.makeText(this@HoursLogActivity, "Failed to load completed sessions", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEmpty(show: Boolean, message: String?) {
        tvEmpty.visibility = if (show) View.VISIBLE else View.GONE
        tvEmpty.text = message ?: ""
        recycler.visibility = if (show) View.GONE else View.VISIBLE
    }
}
