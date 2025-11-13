package com.example.tutorconnect.Views.tutor

import Views.tutor.AvailabilityActivity
import Views.tutor.HoursLogActivity
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.tutorconnect.R
import com.example.tutorconnect.models.TutorSummary
import com.example.tutorconnect.ui.Login
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import models.Booking
import kotlin.math.round

class TutorProfileFragment : Fragment() {

    private lateinit var imgProfile: ImageView
    private lateinit var txtName: TextView
    private lateinit var txtEmail: TextView
    private lateinit var txtPhone: TextView
    private lateinit var txtExpertise: TextView
    private lateinit var txtQualifications: TextView
    private lateinit var btnChangeImage: Button
    private lateinit var btnSave: Button
    private lateinit var btnLogout: Button
    private lateinit var btnLogHours: Button
    private lateinit var btnManageSlots: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedImageUri: Uri? = null
    private var base64Image: String? = null
    private var imageChanged = false

    companion object {
        private const val PICK_IMAGE_REQUEST = 2001
        private const val TAG = "TutorProfileFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutor_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        imgProfile = view.findViewById(R.id.imgTutorProfile)
        txtName = view.findViewById(R.id.txtTutorName)
        txtEmail = view.findViewById(R.id.txtTutorEmail)
        txtPhone = view.findViewById(R.id.txtTutorPhone)
        txtExpertise = view.findViewById(R.id.txtTutorExpertise)
        txtQualifications = view.findViewById(R.id.txtTutorQualifications)
        btnChangeImage = view.findViewById(R.id.btnChangeImage)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnLogout = view.findViewById(R.id.btnLogout)
        btnLogHours = view.findViewById(R.id.btnLogHours)
        btnManageSlots = view.findViewById(R.id.btnManageSlots)

        loadTutorProfile()

        btnChangeImage.setOnClickListener { openImagePicker() }
        imgProfile.setOnClickListener { openImagePicker() }

        btnSave.setOnClickListener { saveTutorProfile() }

        btnManageSlots.setOnClickListener {
            val intent = Intent(requireContext(), AvailabilityActivity::class.java)
            startActivity(intent)
        }

        btnLogHours.setOnClickListener {
            showTutorSummaryBottomSheet()
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Keep onActivityResult for now (compat)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            selectedImageUri?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val input = requireContext().contentResolver.openInputStream(uri)
                        val bytes = input?.use { it.readBytes() }
                        if (bytes == null || bytes.isEmpty()) {
                            Log.w(TAG, "Selected image empty or unreadable")
                            Toast.makeText(requireContext(), "Selected image is empty", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        imgProfile.setImageBitmap(bitmap)
                        imageChanged = true
                        Log.d(TAG, "Selected image encoded (len=${base64Image?.length ?: 0})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error encoding image: ${e.message}", e)
                        Toast.makeText(requireContext(), "Failed to load selected image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Robust load:
     * 1) Load basic fields from Tutors/{uid}
     * 2) Query TutorProfiles where UserId == uid (handles auto-id profiles)
     * 3) Fallback to Tutors/{uid} ProfileImageBase64
     */
    private fun loadTutorProfile() {
        val tutorId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // 1) Basic info from Tutors/{uid}
                val tutorDoc = db.collection("Tutors").document(tutorId).get().await()
                if (!tutorDoc.exists()) {
                    Log.w(TAG, "Tutors/$tutorId does not exist")
                }

                val name = tutorDoc.getString("Name") ?: ""
                val surname = tutorDoc.getString("Surname") ?: ""
                val email = tutorDoc.getString("Email") ?: ""
                val phone = tutorDoc.getString("PhoneNumber") ?: ""
                val expertise = tutorDoc.getString("Expertise") ?: ""
                val qualifications = tutorDoc.getString("Qualifications") ?: ""

                txtName.text = "$name $surname"
                txtEmail.text = "Email: $email"
                txtPhone.text = "Phone: $phone"
                txtExpertise.text = "Expertise: $expertise"
                txtQualifications.text = "Qualifications: $qualifications"

                // 2) Preferred: TutorProfiles where UserId == tutorId
                val profileQuery = db.collection("TutorProfiles")
                    .whereEqualTo("UserId", tutorId)
                    .limit(1)
                    .get()
                    .await()

                if (!profileQuery.isEmpty) {
                    val profileDoc = profileQuery.documents[0]
                    val profileImage = profileDoc.getString("ProfileImageBase64")
                    Log.d(TAG, "TutorProfiles/${profileDoc.id} found; imageLen=${profileImage?.length ?: 0}")
                    if (!profileImage.isNullOrEmpty()) {
                        imgProfile.loadProfileImage(profileImage)
                        base64Image = profileImage
                        return@launch
                    }
                } else {
                    Log.d(TAG, "No TutorProfiles doc found for UserId=$tutorId")
                }

                // 3) Fallback to Tutors/{tutorId}
                val tutorsImage = tutorDoc.getString("ProfileImageBase64")
                if (!tutorsImage.isNullOrEmpty()) {
                    Log.d(TAG, "Loaded ProfileImageBase64 from Tutors/$tutorId (len=${tutorsImage.length})")
                    imgProfile.loadProfileImage(tutorsImage)
                    base64Image = tutorsImage
                } else {
                    Log.d(TAG, "No profile image in TutorProfiles or Tutors; using placeholder")
                    imgProfile.setImageResource(R.drawable.ic_person)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tutor profile", e)
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Robust save:
     * - If a TutorProfiles doc for this user exists -> merge update it
     * - Otherwise create a new TutorProfiles doc with UserId & image
     * - Also merge ProfileImageBase64 into Tutors/{uid} for compatibility
     */
    private fun saveTutorProfile() {
        val tutorId = auth.currentUser?.uid ?: return
        if (!imageChanged) {
            Toast.makeText(requireContext(), "No new image selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val payload = hashMapOf<String, Any>(
                    "ProfileImageBase64" to (base64Image ?: ""),
                    "UserId" to tutorId
                )

                // Try update existing TutorProfiles document (by query)
                val profileQuery = db.collection("TutorProfiles")
                    .whereEqualTo("UserId", tutorId)
                    .limit(1)
                    .get()
                    .await()

                if (!profileQuery.isEmpty) {
                    val profileRef = profileQuery.documents[0].reference
                    profileRef.set(payload, SetOptions.merge()).await()
                    Log.d(TAG, "Updated TutorProfiles/${profileRef.id} with imageLen=${base64Image?.length ?: 0}")
                } else {
                    // create new TutorProfiles doc (auto-id)
                    val newRef = db.collection("TutorProfiles").document()
                    payload["CreatedAt"] = Timestamp.now()
                    newRef.set(payload).await()
                    Log.d(TAG, "Created TutorProfiles/${newRef.id} for user $tutorId")
                }

                // Also merge into Tutors/{tutorId}
                db.collection("Tutors").document(tutorId)
                    .set(mapOf("ProfileImageBase64" to (base64Image ?: "")), SetOptions.merge())
                    .await()
                Log.d(TAG, "Merged ProfileImageBase64 into Tutors/$tutorId")

                imageChanged = false
                Toast.makeText(requireContext(), "✅ Profile picture updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving profile", e)
                Toast.makeText(requireContext(), "❌ Failed to update image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Simple local helper to load base64 image into ImageView
    fun ImageView.loadProfileImage(base64: String?) {
        if (base64.isNullOrEmpty()) return
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            this.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Image decode error", e)
            this.setImageResource(R.drawable.ic_person)
        }
    }

    // --- Manual Firestore mapper + summary logic retained from before ---

    private fun bookingFromDoc(doc: DocumentSnapshot): Booking {
        val b = Booking()
        b.bookingId = doc.getString("BookingId") ?: doc.id
        b.tutorId = doc.getString("TutorId") ?: ""
        b.studentId = doc.getString("StudentId") ?: ""
        b.studentName = doc.getString("StudentName") ?: ""
        b.day = doc.getString("Day") ?: ""
        b.isCompleted = doc.getBoolean("IsCompleted") ?: false
        b.isCancelled = doc.getBoolean("IsCancelled") ?: false
        b.isGroup = doc.getBoolean("IsGroup") ?: false
        b.status = doc.getString("Status") ?: "Unknown"

        // normalize numeric fields to Double to match Booking model expectations
        b.hoursWorked = (doc.get("HoursWorked") as? Number)?.toDouble() ?: 0.0
        b.amountEarned = (doc.get("AmountEarned") as? Number)?.toDouble()
            ?: (doc.get("PricePaid") as? Number)?.toDouble()
                    ?: 0.0

        b.rating = (doc.get("Rating") as? Number)?.toDouble()
        b.comment = doc.getString("Review") ?: doc.getString("Comment")
        b.bookingDate = doc.get("BookingDate") as? Timestamp
        return b
    }

    private suspend fun fetchCompletedBookingsForTutorManual(db: FirebaseFirestore, tutorId: String): List<Booking> {
        return try {
            val snapshot = db.collection("Bookings")
                .whereEqualTo("TutorId", tutorId)
                .whereEqualTo("IsCompleted", true)
                .get().await()
            withContext(Dispatchers.Default) { snapshot.documents.map { bookingFromDoc(it) } }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bookings", e)
            emptyList()
        }
    }

    /**
     * Explicit typed summary container to avoid Kotlin inference/type issues.
     */
    private data class TutorComputedSummary(
        val totalHours: Double,
        val completedSessions: Int,
        val averageRating: Double
    )

    private fun computeTutorSummary(bookings: List<Booking>): TutorComputedSummary {
        var totalHoursAcc: Double = 0.0
        var completedCount: Int = 0
        var ratingSum: Double = 0.0
        var ratingCount: Int = 0

        for (b in bookings) {
            // hoursWorked may be Int, Double, Float or null - normalize to Double
            val hw: Double = when (val raw = b.hoursWorked) {
                null -> 0.0
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            val isCompleted = b.isCompleted ?: false
            if (isCompleted || hw > 0.0) {
                totalHoursAcc += hw
            }
            if (isCompleted) completedCount++

            val r: Double? = when (val rv = b.rating) {
                null -> null
                is Number -> rv.toDouble()
                is String -> rv.toDoubleOrNull()
                else -> null
            }
            if (r != null) {
                ratingSum += r
                ratingCount++
            }
        }

        val avgRating = if (ratingCount > 0) (ratingSum / ratingCount) else 0.0

        return TutorComputedSummary(
            totalHours = totalHoursAcc,
            completedSessions = completedCount,
            averageRating = avgRating
        )
    }

    private fun showTutorSummaryBottomSheet() {
        val tutorId = auth.currentUser?.uid ?: return
        val tutorNameLabel = txtName.text.toString()

        lifecycleScope.launch {
            try {
                val bookings = fetchCompletedBookingsForTutorManual(db, tutorId)
                val summaryData = withContext(Dispatchers.Default) { computeTutorSummary(bookings) }

                val summary = TutorSummary(
                    totalHours = summaryData.totalHours,
                    completedSessions = summaryData.completedSessions,
                    averageRating = summaryData.averageRating
                )

                val sheet = TutorSummarySheet(summary, null) {
                    // start HoursLogActivity and pass tutorId and tutorName
                    val intent = Intent(requireContext(), HoursLogActivity::class.java)
                    intent.putExtra("tutorId", tutorId)
                    intent.putExtra("tutorName", tutorNameLabel)
                    startActivity(intent)
                }
                sheet.show(parentFragmentManager, "tutor_summary_sheet")

            } catch (e: Exception) {
                Log.e(TAG, "Error showing summary", e)
                Toast.makeText(requireContext(), "Failed to load summary", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
