package com.cicero.repostapp

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.ImageView
import android.content.Intent
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
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
        supportActionBar?.setLogo(R.mipmap.ic_launcher_round)
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
                            val avatarUrl = data?.optString("profile_pic_url") ?: ""
                            val fullAvatarUrl = if (avatarUrl.startsWith("http"))
                                avatarUrl else "https://papiqo.com" + avatarUrl
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
                            val statusText = data?.optString("status") ?: ""
                            Glide.with(this@UserProfileActivity)
                                .load(fullAvatarUrl)
                                .placeholder(R.drawable.profile_avatar_placeholder)
                                .error(R.drawable.profile_avatar_placeholder)
                                .circleCrop()
                                .into(findViewById(R.id.image_avatar))

                            val statusImage = if (statusText.equals("true", true)) {
                                R.drawable.ic_status_true
                            } else {
                                R.drawable.ic_status_false
                            }
                            findViewById<ImageView>(R.id.image_status).setImageResource(statusImage)
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
            var (stats, raw) = getStatsFromDb(token, username)
            if (stats == null) {
                fetchAndStoreStats(token, username)
                val result = getStatsFromDb(token, username)
                stats = result.first
                raw = result.second
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

    private suspend fun getStatsFromDb(token: String, username: String): Pair<JSONObject?, String?> {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/insta/profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Pair(null, null)
                val body = resp.body?.string()
                val obj = JSONObject(body ?: "{}")
                Pair(obj.optJSONObject("data") ?: obj, body)
            }
        } catch (_: Exception) {
            Pair(null, null)
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
