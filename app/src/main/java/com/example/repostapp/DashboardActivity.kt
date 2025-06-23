package com.example.repostapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    val userIntent = Intent(this, UserProfileActivity::class.java).apply {
                        putExtra("token", intent.getStringExtra("token"))
                        putExtra("userId", intent.getStringExtra("userId"))
                    }
                    startActivity(userIntent)
                    true
                }
                R.id.nav_insta -> true // stay on dashboard
                R.id.nav_report -> {
                    startActivity(Intent(this, ReportActivity::class.java))
                    true
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_insta
        // TODO: fetch and display today's Instagram posts from official account
    }
}
