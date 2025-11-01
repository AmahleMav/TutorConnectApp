package com.example.tutorconnect.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tutorconnect.R
import com.example.tutorconnect.Views.student.StudentDashboard
import com.example.tutorconnect.Views.admin.AdminDashboard
import com.example.tutorconnect.Views.tutor.TutorDashboard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailField = findViewById(R.id.etEmail)
        passwordField = findViewById(R.id.etPassword)
        loginButton = findViewById(R.id.btnLogin)
        registerLink = findViewById(R.id.tvRegisterLink)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            performLogin(email, password)
        }

        // Open Register activity when clicked
        registerLink.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }
    }

    private fun performLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // Use FirebaseAuth to sign in
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(this, "Failed to get UID", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    // Check role in Firestore
                    db.collection("Admins").document(uid).get()
                        .addOnSuccessListener { adminDoc ->
                            if (adminDoc.exists()) {
                                startActivity(Intent(this, AdminDashboard::class.java))
                                finish()
                                return@addOnSuccessListener
                            }

                            db.collection("Tutors").document(uid).get()
                                .addOnSuccessListener { tutorDoc ->
                                    if (tutorDoc.exists()) {
                                        startActivity(Intent(this, TutorDashboard::class.java))
                                        finish()
                                        return@addOnSuccessListener
                                    }

                                    db.collection("Students").document(uid).get()
                                        .addOnSuccessListener { studentDoc ->
                                            if (studentDoc.exists()) {
                                                startActivity(Intent(this, StudentDashboard::class.java))
                                                finish()
                                            } else {
                                                Toast.makeText(this, "User role not found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                }
                        }
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
