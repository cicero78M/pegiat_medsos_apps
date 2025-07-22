package com.cicero.repostapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import jp.cyrus.tiktok4j.impl.TikTok4jTikTokImpl
import jp.cyrus.tiktok4j.signingServices.impl.TikTok4jSigningService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TikTokLoginActivity : AppCompatActivity() {
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tiktok_login)
        emailInput = findViewById(R.id.edit_email)
        passwordInput = findViewById(R.id.edit_password)
        progress = findViewById(R.id.progress)
        findViewById<Button>(R.id.button_login).setOnClickListener { login() }
    }

    private fun login() {
        val email = emailInput.text.toString().trim()
        val pass = passwordInput.text.toString().trim()
        if (email.isBlank() || pass.isBlank()) return
        progress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = TikTok4jTikTokImpl(TikTok4jSigningService())
                val resp = client.loginWithEmail(email, pass)
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (resp != null) {
                        val intent = Intent().apply { putExtra("cookie", resp.toString()) }
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    } else {
                        Toast.makeText(this@TikTokLoginActivity, "Login gagal", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@TikTokLoginActivity, "Login error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
