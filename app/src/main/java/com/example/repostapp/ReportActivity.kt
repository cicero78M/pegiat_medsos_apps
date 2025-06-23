package com.example.repostapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val ig = findViewById<EditText>(R.id.input_instagram)
        val fb = findViewById<EditText>(R.id.input_facebook)
        val tw = findViewById<EditText>(R.id.input_twitter)
        val tt = findViewById<EditText>(R.id.input_tiktok)
        val yt = findViewById<EditText>(R.id.input_youtube)
        findViewById<Button>(R.id.button_send_report).setOnClickListener {
            val msg = "Laporan terkirim"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
