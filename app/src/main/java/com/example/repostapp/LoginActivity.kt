package com.example.repostapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val nrpInput = findViewById<EditText>(R.id.input_nrp)
        val phoneInput = findViewById<EditText>(R.id.input_phone)
        val loginButton = findViewById<Button>(R.id.button_login)

        loginButton.setOnClickListener {
            val nrp = nrpInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            if (nrp.isBlank() || phone.isBlank()) {
                Toast.makeText(this, "NRP dan nomor telp wajib diisi", Toast.LENGTH_SHORT).show()
            } else {
                login(nrp, phone)
            }
        }
    }

    private fun login(nrp: String, phone: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("nrp", nrp)
                put("whatsapp", phone)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://papiqo.com/api/auth/user-login")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    val success = response.isSuccessful
                    val message = if (success) {
                        ""
                    } else {
                        try {
                            JSONObject(responseBody ?: "{}").optString("message", "Login gagal")
                        } catch (e: Exception) {
                            "Login gagal"
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (success) {
                            val obj = try {
                                JSONObject(responseBody ?: "{}")
                            } catch (e: Exception) {
                                JSONObject()
                            }
                            val token = obj.optString("token", "")
                            val user = obj.optJSONObject("user")
                            val userId = user?.optString("user_id", nrp) ?: nrp
                            val intent = Intent(this@LoginActivity, DashboardActivity::class.java).apply {
                                putExtra("token", token)
                                putExtra("userId", userId)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Gagal terhubung ke server",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
