package com.example.repostapp

import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val userId = prefs.getString("userId", null)
        if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
            validateToken(token, userId)
        } else {
            navigateToLogin()
        }
    }

    private fun validateToken(token: String, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    withContext(Dispatchers.Main) {
                        if (resp.isSuccessful) {
                            navigateToDashboard(token, userId)
                        } else {
                            navigateToLogin()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { navigateToLogin() }
            }
        }
    }

    private fun navigateToDashboard(token: String, userId: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("token", token)
            putExtra("userId", userId)
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
