package com.cicero.repostapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.cicero.repostapp.BuildConfig

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher_foreground)
        supportActionBar?.setDisplayUseLogoEnabled(true)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val userId = prefs.getString("userId", null)
        if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
            validateToken(token, userId)
        }

        findViewById<Button>(R.id.button_open_login).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<Button>(R.id.button_check_update).setOnClickListener {
            checkForUpdates()
        }

        val footer = findViewById<TextView>(R.id.footer_version)
        footer.text = "${getString(R.string.app_name)} versi ${BuildConfig.VERSION_NAME} copyright @cicero"
    }

    // No external storage permission required when using app-specific storage
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
                        }
                    }
                }
            } catch (_: Exception) {
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

    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.github.com/repos/cicero78M/pegiat_medsos_apps/releases/latest")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string()
                    if (resp.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        val tag = json.getString("tag_name").removePrefix("v")
                        if (tag != BuildConfig.VERSION_NAME) {
                            val assets = json.getJSONArray("assets")
                            if (assets.length() > 0) {
                                val url = assets.getJSONObject(0).getString("browser_download_url")
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(intent)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, getString(R.string.up_to_date), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
