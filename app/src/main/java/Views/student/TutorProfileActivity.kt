package Views.student

import Views.shared.ChatDetailActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tutorconnect.R
import com.example.tutorconnect.Views.student.TutorTimeSlotsActivity
import com.example.tutorconnect.utils.loadProfileImage
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

class TutorProfileActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""

    private lateinit var tutorId: String
    private lateinit var tutorName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutor_profile)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val shimmerLayout = findViewById<ShimmerFrameLayout>(R.id.shimmerProfileLayout)
        val profileScrollView = findViewById<ScrollView>(R.id.profileScrollView)
        val imgTutorProfile = findViewById<ImageView>(R.id.imgTutorProfile)
        val txtTutorName = findViewById<TextView>(R.id.txtTutorName)
        val txtTutorEmail = findViewById<TextView>(R.id.txtTutorEmail)
        val txtTutorPhone = findViewById<TextView>(R.id.txtTutorPhone)
        val txtTutorExpertise = findViewById<TextView>(R.id.txtTutorExpertise)
        val txtTutorQualifications = findViewById<TextView>(R.id.txtTutorQualifications)
        val txtTutorDescription = findViewById<TextView>(R.id.txtTutorDescription)
        val txtTutorRating = findViewById<TextView>(R.id.txtTutorRating)
        val reviewsContainer = findViewById<LinearLayout>(R.id.reviewsContainer)
        val btnBookNow = findViewById<Button>(R.id.btnBookNow)
        val btnChatWithMe = findViewById<Button>(R.id.btnChatWithMe)

        tutorId = intent.getStringExtra("tutorId") ?: ""
        tutorName = intent.getStringExtra("tutorName") ?: "Unknown Tutor"

        if (tutorId.isEmpty()) {
            Log.e("TutorProfileActivity", "tutorId missing from Intent!")
            finish()
            return
        }

        txtTutorName.text = tutorName
        txtTutorEmail.text = intent.getStringExtra("tutorEmail") ?: "Email not available"
        txtTutorPhone.text = intent.getStringExtra("tutorPhone") ?: "Phone not available"
        txtTutorExpertise.text = "Expertise: ${intent.getStringExtra("tutorExpertise") ?: "N/A"}"
        txtTutorQualifications.text = "Qualifications: ${intent.getStringExtra("tutorQualifications") ?: "N/A"}"

        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        profileScrollView.visibility = View.GONE

        lifecycleScope.launch {
            loadTutorProfile(imgTutorProfile, txtTutorDescription, txtTutorRating, reviewsContainer)
            fadeTransition(shimmerLayout, profileScrollView)
        }

        btnBookNow.setOnClickListener {
            val intent = Intent(this, TutorTimeSlotsActivity::class.java)
            intent.putExtra("tutorId", tutorId)
            intent.putExtra("tutorName", tutorName)
            startActivity(intent)
        }

        btnChatWithMe.setOnClickListener {
            lifecycleScope.launch { openOrCreateChat() }
        }
    }

    private fun fadeTransition(shimmerLayout: ShimmerFrameLayout, contentView: View) {
        shimmerLayout.animate().alpha(0f).setDuration(300).withEndAction {
            shimmerLayout.stopShimmer()
            shimmerLayout.visibility = View.GONE
            contentView.alpha = 0f
            contentView.visibility = View.VISIBLE
            contentView.animate().alpha(1f).setDuration(400).start()
        }.start()
    }

    /**
     * Load Tutor info, Average Rating, and all reviews from Bookings collection
     */
    private suspend fun loadTutorProfile(
        imgTutorProfile: ImageView,
        txtTutorDescription: TextView,
        txtTutorRating: TextView,
        reviewsContainer: LinearLayout
    ) {
        try {
            coroutineScope {
                // Load profile for image & description
                val profileDoc = async {
                    db.collection("TutorProfiles")
                        .whereEqualTo("UserId", tutorId)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                }

                val tutorDoc = async {
                    db.collection("Tutors")
                        .document(tutorId)
                        .get()
                        .await()
                }

                val profile = profileDoc.await()
                val tutor = tutorDoc.await()

                val imageBase64 = profile?.getString("ProfileImageBase64")
                    ?: tutor.getString("ProfileImageBase64")
                val description = profile?.getString("Description") ?: "No description available."

                // Fetch all bookings with reviews for this tutor
                val bookingsSnapshot = db.collection("Bookings")
                    .whereEqualTo("TutorId", tutorId)
                    .get()
                    .await()

                // Map bookings to reviews, resolving student names
                val reviews = bookingsSnapshot.documents.mapNotNull { doc ->
                    val reviewText = doc.getString("Review")
                    val rating = doc.getLong("Rating")?.toFloat() ?: 0f
                    val studentId = doc.getString("StudentId")
                    val timestamp = doc.getTimestamp("Timestamp")?.toDate()

                    if (!reviewText.isNullOrBlank() && !studentId.isNullOrBlank() && timestamp != null) {
                        val studentDoc = db.collection("Students").document(studentId).get().await()
                        val studentName = listOfNotNull(
                            studentDoc.getString("Name"),
                            studentDoc.getString("Surname")
                        ).joinToString(" ").ifBlank { "Anonymous" }

                        mapOf(
                            "studentName" to studentName,
                            "comment" to reviewText,
                            "rating" to rating,
                            "date" to timestamp
                        )
                    } else null
                }.sortedByDescending { it["date"] as Date } // newest first

                val averageRating = if (reviews.isNotEmpty()) {
                    reviews.map { it["rating"] as Float }.average()
                } else 0.0

                withContext(Dispatchers.Main) {
                    imgTutorProfile.loadProfileImage(imageBase64)
                    txtTutorDescription.text = description
                    txtTutorRating.text = "⭐ ${String.format("%.1f", averageRating)} (${reviews.size} reviews)"

                    reviewsContainer.removeAllViews()
                    reviews.forEach { review ->
                        val reviewView = layoutInflater.inflate(R.layout.item_review, reviewsContainer, false)
                        val txtReviewer = reviewView.findViewById<TextView>(R.id.txtReviewer)
                        val txtComment = reviewView.findViewById<TextView>(R.id.txtComment)
                        val ratingBar = reviewView.findViewById<RatingBar>(R.id.ratingBarReview)

                        txtReviewer.text = review["studentName"]?.toString() ?: "Anonymous"
                        txtComment.text = review["comment"]?.toString() ?: ""
                        ratingBar.rating = (review["rating"] as? Float) ?: 0f

                        reviewsContainer.addView(reviewView)
                    }
                }

                Log.d("TutorProfileActivity", "Loaded ${reviews.size} reviews for tutor $tutorId")
            }
        } catch (e: Exception) {
            Log.e("TutorProfileActivity", "Error loading tutor reviews: ${e.message}", e)
            withContext(Dispatchers.Main) {
                txtTutorDescription.text = "Error loading tutor details."
                txtTutorRating.text = "⭐ 0.0 (0 reviews)"
                imgTutorProfile.setImageResource(R.drawable.ic_person)
            }
        }
    }


    private suspend fun openOrCreateChat() {
        try {
            val querySnapshot = db.collection("Chats")
                .whereEqualTo("StudentId", currentUserId)
                .whereEqualTo("TutorId", tutorId)
                .get()
                .await()

            val chatId = if (!querySnapshot.isEmpty) {
                querySnapshot.documents.first().id
            } else {
                val newChatId = UUID.randomUUID().toString()
                val newChat = hashMapOf(
                    "ChatId" to newChatId,
                    "StudentId" to currentUserId,
                    "TutorId" to tutorId,
                    "TutorName" to tutorName,
                    "CreatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "Messages" to emptyList<Map<String, Any>>()
                )
                db.collection("Chats").document(newChatId).set(newChat).await()
                newChatId
            }

            val studentDoc = db.collection("Students").document(currentUserId).get().await()
            val studentName = listOfNotNull(
                studentDoc.getString("Name"),
                studentDoc.getString("Surname")
            ).joinToString(" ").ifBlank { "Student" }

            val intent = Intent(this, ChatDetailActivity::class.java).apply {
                putExtra("ChatId", chatId)
                putExtra("TutorId", tutorId)
                putExtra("StudentId", currentUserId)
                putExtra("tutorName", tutorName)
                putExtra("studentName", studentName)
            }
            startActivity(intent)

        } catch (e: Exception) {
            Log.e("TutorProfileActivity", "Failed to open chat: ${e.message}", e)
        }
    }
}
