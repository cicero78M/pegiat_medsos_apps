package com.cicero.repostapp

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
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
        supportActionBar?.hide()
        setContentView(R.layout.activity_login)

        val authPrefs = getSharedPreferences("auth", MODE_PRIVATE)
        val savedToken = authPrefs.getString("token", null)
        val savedUser = authPrefs.getString("userId", null)
        if (!savedToken.isNullOrBlank() && !savedUser.isNullOrBlank()) {
            validateToken(savedToken, savedUser)
        }
        val nrpInput = findViewById<EditText>(R.id.input_nrp)
        val passwordInput = findViewById<EditText>(R.id.input_password)
        val showPasswordBox = findViewById<CheckBox>(R.id.checkbox_show_password)
        val saveLoginBox = findViewById<CheckBox>(R.id.checkbox_save_login)
        val loginButton = findViewById<Button>(R.id.button_login)
        val registerButton = findViewById<Button>(R.id.button_register)

        val loginPrefs = getSharedPreferences("login", MODE_PRIVATE)
        val savedNrp = loginPrefs.getString("nrp", "")
        val savedPass = loginPrefs.getString("password", "")
        if (!savedNrp.isNullOrBlank() && !savedPass.isNullOrBlank()) {
            nrpInput.setText(savedNrp)
            passwordInput.setText(savedPass)
            saveLoginBox.isChecked = true
        }

        showPasswordBox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                passwordInput.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
        }

        loginButton.setOnClickListener {
            val nrp = nrpInput.text.toString().trim()
            val phone = passwordInput.text.toString().trim()
            val save = saveLoginBox.isChecked
            if (nrp.isBlank() || phone.isBlank()) {
                Toast.makeText(this, "NRP dan password wajib diisi", Toast.LENGTH_SHORT).show()
            } else {
                login(nrp, phone, save)
            }
        }

        registerButton.setOnClickListener {
            val message = getString(R.string.whatsapp_message_registration)
            val url = "https://wa.me/62895601093339?text=" + Uri.encode(message)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun login(nrp: String, phone: String, saveLogin: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("nrp", nrp)
                put("whatsapp", phone)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/auth/user-login")
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
                            val prefs = getSharedPreferences("auth", MODE_PRIVATE)
                            prefs.edit {
                                putString("token", token)
                                putString("userId", userId)
                            }
                            val loginPrefs = getSharedPreferences("login", MODE_PRIVATE)
                            if (saveLogin) {
                                loginPrefs.edit {
                                    putString("nrp", nrp)
                                    putString("password", phone)
                                }
                            } else {
                                loginPrefs.edit { clear() }
                            }

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

    private fun validateToken(token: String, userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
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

}
