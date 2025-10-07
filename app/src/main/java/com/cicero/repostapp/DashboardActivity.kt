package com.cicero.repostapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.view.Menu
import android.view.MenuItem

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher_foreground)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        val pageTitles = listOf(
            "Profil",
            "Tugas Resmi",
            "Tugas Khusus"
        )

        val token = intent.getStringExtra("token")
        val userId = intent.getStringExtra("userId")

        val fragments = listOf(
            UserProfileFragment.newInstance(userId, token),
            DashboardFragment.newInstance(userId, token),
            SpecialTaskFragment.newInstance(userId, token)
        )

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        viewPager.isUserInputEnabled = false

        supportActionBar?.title = pageTitles[1]

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> { viewPager.currentItem = 0; true }
                R.id.nav_insta -> { viewPager.currentItem = 1; true }
                R.id.nav_special -> { viewPager.currentItem = 2; true }
                else -> false
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> { bottomNav.selectedItemId = R.id.nav_profile }
                    1 -> { bottomNav.selectedItemId = R.id.nav_insta }
                    2 -> { bottomNav.selectedItemId = R.id.nav_special }
                }
                supportActionBar?.title = pageTitles[position]
            }
        })

        viewPager.currentItem = 1
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, R.id.menu_logout, 0, "Logout")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                val prefs = getSharedPreferences("auth", MODE_PRIVATE)
                prefs.edit().clear().apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
