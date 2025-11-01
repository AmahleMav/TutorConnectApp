package com.example.tutorconnect.Views.tutor

import Views.tutor.fragments.TutorBookingsFragment
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tutorconnect.R
import com.example.tutorconnect.Views.tutor.fragments.*
import com.google.android.material.bottomnavigation.BottomNavigationView

class TutorDashboard : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutor_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.tutorBottomNav)

        // Default fragment when dashboard loads
        loadFragment(TutorHomeFragment())

        // Handle navigation item selection
        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> TutorHomeFragment()
                R.id.nav_bookings -> TutorBookingsFragment()
                R.id.nav_inbox -> TutorInboxFragment()
                R.id.nav_profile -> TutorProfileFragment()
                else -> TutorHomeFragment()
            }
            loadFragment(selectedFragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.tutor_fragment_container, fragment)
            .commit()
    }
}
