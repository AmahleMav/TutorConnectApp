package com.example.tutorconnect.Views.student

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tutorconnect.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import Views.student.fragments.*

class StudentDashboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // Handle deep link navigation
        val navigateTo = intent.getStringExtra("navigateTo")
        val initialFragment = when (navigateTo) {
            "bookings" -> BookingsFragment()
            "tutors" -> TutorsFragment()
            "messages" -> MessagesFragment()
            "profile" -> ProfileFragment()
            else -> HomeFragment()
        }
        loadFragment(initialFragment)

        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_tutors -> TutorsFragment()
                R.id.nav_bookings -> BookingsFragment()
                R.id.nav_messages -> MessagesFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> null
            }
            selectedFragment?.let { loadFragment(it) }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.student_fragment_container, fragment)
            .commit()
    }
}