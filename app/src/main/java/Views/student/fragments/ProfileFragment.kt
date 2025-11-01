package com.example.tutorconnect.Views.student

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.tutorconnect.R
import com.example.tutorconnect.ui.Login
import com.example.tutorconnect.utils.encodeImageToBase64
import com.example.tutorconnect.utils.loadProfileImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {

    private lateinit var imgProfile: ImageView
    private lateinit var txtName: TextView
    private lateinit var txtEmail: TextView
    private lateinit var txtPhone: TextView
    private lateinit var btnChangeImage: Button
    private lateinit var btnSave: Button
    private lateinit var btnLogout: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedImageUri: Uri? = null
    private var base64Image: String? = null
    private var imageChanged = false

    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // UI Components
        imgProfile = view.findViewById(R.id.imgProfile)
        txtName = view.findViewById(R.id.txtProfileName)
        txtEmail = view.findViewById(R.id.txtProfileEmail)
        txtPhone = view.findViewById(R.id.txtProfilePhone)
        btnChangeImage = view.findViewById(R.id.btnChangeImage)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnLogout = view.findViewById(R.id.btnLogout)

        // Load user data
        loadStudentProfile()

        // Image change handlers
        btnChangeImage.setOnClickListener { openImagePicker() }
        imgProfile.setOnClickListener { openImagePicker() } // Optional: tap image to change

        // Save new profile image
        btnSave.setOnClickListener { saveStudentProfile() }

        // Logout logic
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

    /**
     * Opens gallery picker for selecting a new image
     */
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    /**
     * Handles selected image and encodes it to Base64
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedImageUri = data?.data
            selectedImageUri?.let {
                lifecycleScope.launch {
                    try {
                        base64Image = encodeImageToBase64(requireContext(), it)
                        imgProfile.loadProfileImage(base64Image)
                        imageChanged = true
                    } catch (e: Exception) {
                        Log.e("ProfileFragment", "Error encoding image: ${e.message}")
                        Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Loads current student profile info from Firestore
     */
    private fun loadStudentProfile() {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val doc = db.collection("Students").document(userId).get().await()
                if (doc.exists()) {
                    val name = doc.getString("Name") ?: ""
                    val surname = doc.getString("Surname") ?: ""
                    val email = doc.getString("Email") ?: ""
                    val phone = doc.getString("PhoneNumber") ?: ""
                    val image = doc.getString("ProfileImageBase64")

                    txtName.text = "$name $surname"
                    txtEmail.text = "Email: $email"
                    txtPhone.text = "Phone: $phone"

                    if (!image.isNullOrEmpty()) {
                        imgProfile.loadProfileImage(image)
                        base64Image = image
                    } else {
                        imgProfile.setImageResource(R.drawable.ic_person)
                    }
                } else {
                    Toast.makeText(requireContext(), "Profile not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error loading profile", e)
                Toast.makeText(requireContext(), "Error loading profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Saves updated profile image to Firestore
     */
    private fun saveStudentProfile() {
        val userId = auth.currentUser?.uid ?: return

        if (!imageChanged) {
            Toast.makeText(requireContext(), "No new image selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                db.collection("Students").document(userId)
                    .update("ProfileImageBase64", base64Image)
                    .await()

                imageChanged = false
                Toast.makeText(requireContext(), "✅ Profile picture updated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error updating profile image", e)
                Toast.makeText(requireContext(), "❌ Failed to update profile image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
