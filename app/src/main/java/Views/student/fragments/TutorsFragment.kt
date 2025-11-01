package Views.student.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorconnect.R
import Views.student.adapters.TutorAdapter
import Views.student.TutorProfileActivity
import com.google.firebase.firestore.FirebaseFirestore
import models.Tutor

class TutorsFragment : Fragment() {

    private lateinit var recyclerTutors: RecyclerView
    private lateinit var shimmerContainer: LinearLayout
    private lateinit var etSearchTutors: EditText

    private val db = FirebaseFirestore.getInstance()
    private val tutorsList = mutableListOf<Tutor>()
    private val filteredTutors = mutableListOf<Tutor>()
    private lateinit var adapter: TutorAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tutors, container, false)

        recyclerTutors = view.findViewById(R.id.recyclerTutors)
        shimmerContainer = view.findViewById(R.id.shimmerContainer)
        etSearchTutors = view.findViewById(R.id.etSearchTutors)

        recyclerTutors.layoutManager = LinearLayoutManager(requireContext())
        adapter = TutorAdapter(filteredTutors) { selectedTutor ->
            openTutorProfile(selectedTutor)
        }
        recyclerTutors.adapter = adapter

        etSearchTutors.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTutors(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        showShimmer(true, animated = false)
        fetchTutors()

        return view
    }

    private fun fetchTutors() {
        db.collection("Tutors").get()
            .addOnSuccessListener { tutorDocs ->
                tutorsList.clear()

                val tutorIds = tutorDocs.documents.map { it.getString("UserId") ?: "" }

                db.collection("TutorProfiles")
                    .whereIn("UserId", tutorIds)
                    .get()
                    .addOnSuccessListener { profilesSnapshot ->

                        val profileMap = profilesSnapshot.documents
                            .mapNotNull { doc ->
                                val id = doc.getString("UserId") ?: return@mapNotNull null
                                mapOf(
                                    "OneOnOneHourlyRate" to (doc.getDouble("OneOnOneHourlyRate") ?: 100.0),
                                    "GroupHourlyRate" to (doc.getDouble("GroupHourlyRate") ?: 100.0),
                                    "Description" to (doc.getString("Description") ?: "No description provided"),
                                    "ProfileImageBase64" to (doc.getString("ProfileImageBase64") ?: "")
                                ).let { id to it }
                            }.toMap()

                        for (doc in tutorDocs.documents) {
                            val userId = doc.getString("UserId") ?: continue
                            val rawImage = doc.getString("ProfileImageBase64") ?: ""
                            val profileData = profileMap[userId]

                            val tutor = Tutor(
                                UserId = userId,
                                Name = doc.getString("Name") ?: "",
                                Surname = doc.getString("Surname") ?: "",
                                Email = doc.getString("Email") ?: "",
                                PhoneNumber = doc.getString("PhoneNumber") ?: "",
                                Expertise = doc.getString("Expertise") ?: "",
                                Qualifications = doc.getString("Qualifications") ?: "",
                                ProfileImageBase64 = profileData?.get("ProfileImageBase64") as? String ?: rawImage,
                                Description = profileData?.get("Description") as? String ?: "No description provided",
                                AverageRating = doc.getDouble("AverageRating") ?: 0.0,
                                OneOnOneHourlyRate = profileData?.get("OneOnOneHourlyRate") as? Double ?: 100.0
                            )

                            tutorsList.add(tutor)
                        }

                        filterTutors(etSearchTutors.text.toString())
                        showShimmer(false, animated = true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("TutorsFragment", "Failed to fetch profiles: ${e.message}")
                        showShimmer(false, animated = true)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("TutorsFragment", "Failed to fetch tutors: ${e.message}")
                showShimmer(false, animated = true)
            }
    }

    private fun filterTutors(query: String) {
        val lowerQuery = query.lowercase()
        filteredTutors.clear()
        filteredTutors.addAll(tutorsList.filter {
            it.Name.lowercase().contains(lowerQuery) ||
                    it.Surname.lowercase().contains(lowerQuery) ||
                    it.Expertise.lowercase().contains(lowerQuery)
        })
        adapter.notifyDataSetChanged()
    }

    private fun openTutorProfile(tutor: Tutor) {
        val intent = Intent(requireContext(), TutorProfileActivity::class.java).apply {
            putExtra("tutorId", tutor.UserId)
            putExtra("tutorName", "${tutor.Name} ${tutor.Surname}")
            putExtra("tutorEmail", tutor.Email)
            putExtra("tutorPhone", tutor.PhoneNumber)
            putExtra("tutorExpertise", tutor.Expertise)
            putExtra("tutorQualifications", tutor.Qualifications)
            putExtra("tutorDescription", tutor.Description)
            putExtra("tutorRating", tutor.AverageRating)
            putExtra("tutorHourlyRate", tutor.OneOnOneHourlyRate)
        }
        startActivity(intent)
    }

    private fun showShimmer(show: Boolean, animated: Boolean) {
        if (show) {
            shimmerContainer.visibility = View.VISIBLE
            recyclerTutors.visibility = View.GONE
        } else {
            if (animated) {
                val fadeOut = AlphaAnimation(1f, 0f).apply { duration = 300; fillAfter = true }
                shimmerContainer.startAnimation(fadeOut)
                recyclerTutors.alpha = 0f
                recyclerTutors.visibility = View.VISIBLE
                recyclerTutors.animate().alpha(1f).setDuration(400).start()
                shimmerContainer.postDelayed({ shimmerContainer.visibility = View.GONE }, 350)
            } else {
                shimmerContainer.visibility = View.GONE
                recyclerTutors.visibility = View.VISIBLE
            }
        }
    }
}
