package com.cicero.repostapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_splash)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val userId = prefs.getString("userId", null)
        if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
            validateToken(token, userId)
        } else {
            navigateToLanding()
        }
    }

    private fun validateToken(token: String, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpProvider.getClient(this@SplashActivity)
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
                            navigateToLanding()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { navigateToLanding() }
            }
        }
    }

    private fun navigateToLanding() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToDashboard(token: String, userId: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("token", token)
            putExtra("userId", userId)
        }
        startActivity(intent)
        finish()
    }
}
