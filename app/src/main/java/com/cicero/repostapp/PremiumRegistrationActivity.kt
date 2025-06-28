package com.cicero.repostapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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

class PremiumRegistrationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium_registration)

        val amount = 50000 + (100..999).random()
        val amountView = findViewById<TextView>(R.id.text_amount)
        amountView.text = "Rp. ${String.format("%,d", amount).replace(',', '.')}"

        val sessionEndView = findViewById<TextView>(R.id.text_session_end)
        val endTime = System.currentTimeMillis() + 10 * 60 * 1000
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        sessionEndView.text = getString(R.string.session_end_format, sdf.format(java.util.Date(endTime)))

        val username = findViewById<EditText>(R.id.input_username)
        intent.getStringExtra("username")?.takeIf { it.isNotBlank() }?.let {
            username.setText(it)
        }
        username.isEnabled = false
        val nama = findViewById<EditText>(R.id.input_nama_rekening)
        val nomor = findViewById<EditText>(R.id.input_nomor_rekening)
        val phone = findViewById<EditText>(R.id.input_phone)
        val button = findViewById<Button>(R.id.button_submit)
        val cancelButton = findViewById<Button>(R.id.button_cancel)

        cancelButton.setOnClickListener { finish() }

        button.setOnClickListener {
            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
            val token = prefs.getString("token", "") ?: ""
            val userId = prefs.getString("userId", "") ?: ""
            if (token.isBlank() || userId.isBlank()) {
                Toast.makeText(this, "Anda belum login", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val usernameVal = username.text.toString().trim()
            val namaVal = nama.text.toString().trim()
            val nomorVal = nomor.text.toString().trim()
            val phoneVal = phone.text.toString().trim()

            CoroutineScope(Dispatchers.IO).launch {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("username", usernameVal)
                    put("nama_rekening", namaVal)
                    put("nomor_rekening", nomorVal)
                    put("phone", phoneVal)
                    put("amount", amount)
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://papiqo.com/api/subscription-registrations")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                val success = try {
                    client.newCall(request).execute().use { it.isSuccessful }
                } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@PremiumRegistrationActivity, "Pendaftaran tersimpan", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@PremiumRegistrationActivity, "Gagal mendaftar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
