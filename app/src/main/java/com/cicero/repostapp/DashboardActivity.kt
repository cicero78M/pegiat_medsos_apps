package com.cicero.repostapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher_foreground)
        supportActionBar?.setDisplayUseLogoEnabled(true)

        val token = intent.getStringExtra("token")
        val userId = intent.getStringExtra("userId")

        val fragments = listOf(
            UserProfileFragment.newInstance(userId, token),
            DashboardFragment.newInstance(userId, token),
            InstagramToolsFragment()
        )

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        viewPager.isUserInputEnabled = false

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> { viewPager.currentItem = 0; true }
                R.id.nav_insta -> { viewPager.currentItem = 1; true }
                R.id.nav_instagram_tools -> { viewPager.currentItem = 2; true }
                else -> false
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> bottomNav.selectedItemId = R.id.nav_profile
                    1 -> bottomNav.selectedItemId = R.id.nav_insta
                    2 -> bottomNav.selectedItemId = R.id.nav_instagram_tools
                }
            }
        })

        viewPager.currentItem = 1
    }
}
