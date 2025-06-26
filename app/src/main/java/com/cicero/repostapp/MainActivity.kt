package com.cicero.repostapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher_round)
        supportActionBar?.setDisplayUseLogoEnabled(true)



        findViewById<Button>(R.id.button_open_login).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    // No external storage permission required when using app-specific storage
}
