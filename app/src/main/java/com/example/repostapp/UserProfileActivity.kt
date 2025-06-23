package com.example.repostapp

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.content.Intent
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UserProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        val userId = intent.getStringExtra("userId") ?: ""
        val token = intent.getStringExtra("token") ?: ""
        findViewById<Button>(R.id.button_logout).setOnClickListener {
            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
            prefs.edit { clear() }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        if (userId.isNotBlank() && token.isNotBlank()) {
            fetchProfile(userId, token)
        }
    }

    private fun fetchProfile(userId: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            val data = try {
                                val obj = JSONObject(body ?: "{}")
                                obj.optJSONObject("data")
                            } catch (_: Exception) {
                                null
                            }
                            val insta = data?.optString("insta") ?: ""
                            findViewById<TextView>(R.id.text_username).text =
                                "@" + insta
                            findViewById<TextView>(R.id.text_name).text =
                                (data?.optString("title") ?: "") + " " + (data?.optString("nama") ?: "")
                            findViewById<TextView>(R.id.text_nrp).text =
                                (data?.optString("user_id") ?: userId)
                            findViewById<TextView>(R.id.text_client_id).text =
                                (data?.optString("client_id") ?: "")
                            findViewById<TextView>(R.id.text_satfung).text =
                                (data?.optString("divisi") ?: "")
                            findViewById<TextView>(R.id.text_jabatan).text =
                                (data?.optString("jabatan") ?: "")
                            findViewById<TextView>(R.id.text_tiktok).text =
                                (data?.optString("tiktok") ?: "")
                            findViewById<TextView>(R.id.text_status).text =
                                (data?.optString("status") ?: "")
                            fetchStats(token, insta)
                        } else {
                            Toast.makeText(this@UserProfileActivity, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UserProfileActivity, "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchStats(token: String, username: String) {
        if (username.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val stats = getStatsFromDb(token, username) ?: run {
                fetchAndStoreStats(token, username)
                getStatsFromDb(token, username)
            }
            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.stat_posts).text =
                    (stats?.optInt("post_count") ?: 0).toString()
                findViewById<TextView>(R.id.stat_followers).text =
                    (stats?.optInt("follower_count") ?: 0).toString()
                findViewById<TextView>(R.id.stat_following).text =
                    (stats?.optInt("following_count") ?: 0).toString()
            }
        }
    }

    private suspend fun getStatsFromDb(token: String, username: String): JSONObject? {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/insta/profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                val obj = JSONObject(body ?: "{}")
                obj.optJSONObject("data") ?: obj
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchAndStoreStats(token: String, username: String) {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/insta/rapid-profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }
}
