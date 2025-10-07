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
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private val httpClient by lazy { OkHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_login)

        val authPrefs = SecurePreferences.getAuthPrefs(this)
        val savedToken = authPrefs.getString("token", null)
        val savedUser = authPrefs.getString("userId", null)
        if (!savedToken.isNullOrBlank() && !savedUser.isNullOrBlank()) {
            validateToken(savedToken, savedUser)
        }
        val nrpInput = findViewById<EditText>(R.id.input_nrp)
        val passwordInput = findViewById<EditText>(R.id.input_password)
        val nrpLayout = findViewById<TextInputLayout>(R.id.layout_nrp)
        val passwordLayout = findViewById<TextInputLayout>(R.id.layout_password)
        val showPasswordBox = findViewById<CheckBox>(R.id.checkbox_show_password)
        val saveLoginBox = findViewById<CheckBox>(R.id.checkbox_save_login)
        val loginButton = findViewById<Button>(R.id.button_login)
        val registerButton = findViewById<Button>(R.id.button_register)

        val loginPrefs = getSharedPreferences("login", MODE_PRIVATE)
        val savedNrp = loginPrefs.getString("nrp", "")
        if (!savedNrp.isNullOrBlank()) {
            nrpInput.setText(savedNrp)
            saveLoginBox.isChecked = true
        }
        if (loginPrefs.contains("password")) {
            loginPrefs.edit { remove("password") }
        }

        showPasswordBox.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                passwordInput.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
        }

        nrpInput.addTextChangedListener { text ->
            if (!text.isNullOrBlank()) {
                nrpLayout.error = null
            }
        }

        passwordInput.addTextChangedListener { text ->
            if (!text.isNullOrBlank()) {
                passwordLayout.error = null
            }
        }

        loginButton.setOnClickListener {
            val nrp = nrpInput.text.toString().trim()
            val phone = passwordInput.text.toString().trim()
            val save = saveLoginBox.isChecked

            var hasError = false
            if (nrp.isBlank()) {
                nrpLayout.error = getString(R.string.error_nrp_required)
                hasError = true
            } else {
                nrpLayout.error = null
            }

            if (phone.isBlank()) {
                passwordLayout.error = getString(R.string.error_password_required)
                hasError = true
            } else {
                passwordLayout.error = null
            }

            if (hasError) {
                return@setOnClickListener
            }

            login(nrp, phone, save)
        }

        registerButton.setOnClickListener {
            val message = getString(R.string.whatsapp_message_registration)
            val url = "https://wa.me/62895601093339?text=" + Uri.encode(message)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun login(nrp: String, phone: String, saveLogin: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("nrp", nrp)
                put("whatsapp", phone)
                put("password", phone)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/auth/user-login")
                .post(body)
                .build()

            try {
                val call = httpClient.newCall(request)
                coroutineContext[Job]?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) {
                        call.cancel()
                    }
                }

                call.execute().use { response ->
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
                        if (!isActive) {
                            return@withContext
                        }
                        if (success) {
                            val obj = try {
                                JSONObject(responseBody ?: "{}")
                            } catch (e: Exception) {
                                JSONObject()
                            }
                            val token = obj.optString("token", "")
                            val user = obj.optJSONObject("user")
                            val userId = user?.optString("user_id", nrp) ?: nrp
                            val prefs = SecurePreferences.getAuthPrefs(this@LoginActivity)
                            prefs.edit {
                                putString("token", token)
                                putString("userId", userId)
                            }
                            val loginPrefs = getSharedPreferences("login", MODE_PRIVATE)
                            if (saveLogin) {
                                loginPrefs.edit {
                                    putString("nrp", nrp)
                                    remove("password")
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
                if (e is CancellationException || !isActive) {
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (!isActive) {
                        return@withContext
                    }
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
        lifecycleScope.launch(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                val call = httpClient.newCall(request)
                coroutineContext[Job]?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) {
                        call.cancel()
                    }
                }

                call.execute().use { resp ->
                    withContext(Dispatchers.Main) {
                        if (!isActive) {
                            return@withContext
                        }
                        if (resp.isSuccessful) {
                            navigateToDashboard(token, userId)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException || !isActive) {
                    return@launch
                }
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
