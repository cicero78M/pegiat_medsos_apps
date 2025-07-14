package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AutopostFragment : Fragment() {

    companion object {
        fun newInstance(): AutopostFragment = AutopostFragment()
    }

    private val serverUrl = "http://10.0.2.2:3000" // change to your server

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_autopost, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val icon = view.findViewById<ImageView>(R.id.instagram_icon)
        val check = view.findViewById<ImageView>(R.id.check_mark)
        val start = view.findViewById<Button>(R.id.button_start)

        icon.setOnClickListener { showLoginDialog(icon, check) }
        start.setOnClickListener {
            Toast.makeText(requireContext(), "Start pressed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoginDialog(icon: ImageView, check: ImageView) {
        val view = layoutInflater.inflate(R.layout.dialog_login, null)
        val userInput = view.findViewById<EditText>(R.id.edit_username)
        val passInput = view.findViewById<EditText>(R.id.edit_password)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Login") { _, _ ->
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (user.isBlank() || pass.isBlank()) {
                    Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    performLogin(user, pass, icon, check)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogin(username: String, password: String, icon: ImageView, check: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val body = JSONObject().apply {
                put("username", username)
                put("password", password)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/login")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string()
                    val obj = JSONObject(text ?: "{}")
                    when {
                        resp.isSuccessful -> {
                            val pic = obj.getJSONObject("user").getString("profilePic")
                            withContext(Dispatchers.Main) {
                                Glide.with(this@AutopostFragment).load(pic).into(icon)
                                check.visibility = View.VISIBLE
                            }
                        }
                        obj.optBoolean("twoFactorRequired") -> {
                            val ident = obj.optString("twoFactorIdentifier")
                            withContext(Dispatchers.Main) { showTwoFactorDialog(username, ident, icon, check) }
                        }
                        obj.optBoolean("checkpoint") -> {
                            withContext(Dispatchers.Main) { showCheckpointDialog(username, icon, check) }
                        }
                        else -> {
                            val err = obj.optString("error", "Login gagal")
                            withContext(Dispatchers.Main) { Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal terhubung", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun showTwoFactorDialog(username: String, identifier: String, icon: ImageView, check: ImageView) {
        val view = layoutInflater.inflate(R.layout.dialog_two_factor, null)
        val codeInput = view.findViewById<EditText>(R.id.edit_code)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Verify") { _, _ ->
                val code = codeInput.text.toString().trim()
                if (code.isNotEmpty()) {
                    verifyTwoFactor(username, identifier, code, icon, check)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun verifyTwoFactor(username: String, identifier: String, code: String, icon: ImageView, check: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val body = JSONObject().apply {
                put("username", username)
                put("twoFactorIdentifier", identifier)
                put("twoFactorCode", code)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/login")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string()
                    val obj = JSONObject(text ?: "{}")
                    if (resp.isSuccessful) {
                        val pic = obj.getJSONObject("user").getString("profilePic")
                        withContext(Dispatchers.Main) {
                            Glide.with(this@AutopostFragment).load(pic).into(icon)
                            check.visibility = View.VISIBLE
                        }
                    } else {
                        val err = obj.optString("error", "Verifikasi gagal")
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal terhubung", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun showCheckpointDialog(username: String, icon: ImageView, check: ImageView) {
        val view = layoutInflater.inflate(R.layout.dialog_checkpoint, null)
        val codeInput = view.findViewById<EditText>(R.id.edit_checkpoint)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Submit") { _, _ ->
                val code = codeInput.text.toString().trim()
                if (code.isNotEmpty()) {
                    verifyCheckpoint(username, code, icon, check)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun verifyCheckpoint(username: String, code: String, icon: ImageView, check: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val body = JSONObject().apply {
                put("username", username)
                put("checkpointCode", code)
            }.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/login")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string()
                    val obj = JSONObject(text ?: "{}")
                    if (resp.isSuccessful) {
                        val pic = obj.getJSONObject("user").getString("profilePic")
                        withContext(Dispatchers.Main) {
                            Glide.with(this@AutopostFragment).load(pic).into(icon)
                            check.visibility = View.VISIBLE
                        }
                    } else {
                        val err = obj.optString("error", "Checkpoint gagal")
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal terhubung", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
