package com.example.tutorconnect.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.tutorconnect.R
import com.example.tutorconnect.models.Register
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Register : AppCompatActivity() {

    private lateinit var nameEt: EditText
    private lateinit var surnameEt: EditText
    private lateinit var phoneEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var registerBtn: Button
    private lateinit var errorTv: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameEt = findViewById(R.id.etName)
        surnameEt = findViewById(R.id.etSurname)
        phoneEt = findViewById(R.id.etPhone)
        emailEt = findViewById(R.id.etEmail)
        passwordEt = findViewById(R.id.etPassword)
        registerBtn = findViewById(R.id.btnRegister)
        errorTv = findViewById(R.id.tvError)

        registerBtn.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val user = Register(
            name = nameEt.text.toString().trim(),
            surname = surnameEt.text.toString().trim(),
            phoneNumber = phoneEt.text.toString().trim(),
            email = emailEt.text.toString().trim(),
            password = passwordEt.text.toString(),
            role = "Student", // ✅ Automatically assign Student role
            qualifications = null,
            expertise = null
        )

        if (user.email.isBlank() || user.password.isBlank()) {
            errorTv.text = "Email and password are required."
            return
        }

        // ✅ Create user in Firebase Authentication
        auth.createUserWithEmailAndPassword(user.email, user.password)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val uid = authTask.result?.user?.uid
                    if (uid != null) {
                        user.userId = uid
                        saveUserToFirestore(user)
                    }
                } else {
                    errorTv.text = "Registration failed: ${authTask.exception?.message}"
                }
            }
    }

    private fun saveUserToFirestore(user: Register) {
        val collection = "Students" // ✅ Always save under Students

        val userMap = hashMapOf(
            "UserId" to user.userId,
            "Name" to user.name,
            "Surname" to user.surname,
            "Email" to user.email,
            "PhoneNumber" to user.phoneNumber,
            "Role" to user.role
        )

        db.collection(collection).document(user.userId!!)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Registered successfully!", Toast.LENGTH_LONG).show()
                navigateToStudentDashboard()
            }
            .addOnFailureListener { e ->
                errorTv.text = "Firestore error: ${e.message}"
            }
    }

    private fun navigateToStudentDashboard() {
        val intent = Intent(this, com.example.tutorconnect.Views.student.StudentDashboard::class.java)
        startActivity(intent)
        finish()
    }
}
