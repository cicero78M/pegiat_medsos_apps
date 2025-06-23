package com.example.repostapp

import android.os.Bundle
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

class UserProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        val userId = intent.getStringExtra("userId") ?: ""
        val token = intent.getStringExtra("token") ?: ""
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
                            findViewById<TextView>(R.id.text_nrp).text = "NRP: " + (data?.optString("user_id") ?: userId)
                            findViewById<TextView>(R.id.text_name).text = "Nama: " + (data?.optString("nama") ?: "")
                            findViewById<TextView>(R.id.text_phone).text = "Telepon: " + (data?.optString("whatsapp") ?: "")
                            findViewById<TextView>(R.id.text_satfung).text =
                                "Satfung: " + (data?.optString("divisi") ?: "")
                            findViewById<TextView>(R.id.text_jabatan).text =
                                "Jabatan: " + (data?.optString("jabatan") ?: "")
                            findViewById<TextView>(R.id.text_ig).text =
                                "Username IG: " + (data?.optString("insta") ?: "")
                            findViewById<TextView>(R.id.text_tiktok).text =
                                "Username TikTok: " + (data?.optString("tiktok") ?: "")
                            findViewById<TextView>(R.id.text_status).text =
                                "Status: " + (data?.optString("status") ?: "")
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
}
