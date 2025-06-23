package com.example.repostapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UserProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val nrp = intent.getStringExtra("nrp") ?: ""
        val name = intent.getStringExtra("name") ?: ""
        val phone = intent.getStringExtra("phone") ?: ""

        findViewById<TextView>(R.id.text_nrp).text = "NRP: $nrp"
        findViewById<TextView>(R.id.text_name).text = "Nama: $name"
        findViewById<TextView>(R.id.text_phone).text = "Telepon: $phone"
    }
}
