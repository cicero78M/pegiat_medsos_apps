package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class TiktokFragment : Fragment(R.layout.fragment_tiktok) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val usernameInput: TextInputEditText = view.findViewById(R.id.input_tiktok_username)
        val passwordInput: TextInputEditText = view.findViewById(R.id.input_tiktok_password)
        val statusView: TextView = view.findViewById(R.id.text_tiktok_status)
        val loginButton: MaterialButton = view.findViewById(R.id.button_tiktok_login)

        loginButton.setOnClickListener {
            val user = usernameInput.text?.toString()?.trim().orEmpty()
            val pass = passwordInput.text?.toString()?.trim().orEmpty()
            if (user.isNotBlank() && pass.isNotBlank()) {
                performLogin(user, pass, statusView)
            } else {
                Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogin(user: String, pass: String, statusView: TextView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = "https://tikwm.com/api/user/info/?unique_id=" +
                    URLEncoder.encode(user, "UTF-8")
            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string()
                    val data = try {
                        JSONObject(body ?: "{}").optJSONObject("data")
                    } catch (_: Exception) { null }
                    withContext(Dispatchers.Main) {
                        if (resp.isSuccessful && data != null) {
                            statusView.text = "@" + data.optString("unique_id")
                            Toast.makeText(requireContext(), "Login berhasil", Toast.LENGTH_SHORT).show()
                        } else {
                            statusView.text = getString(R.string.not_logged_in)
                            Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    statusView.text = getString(R.string.not_logged_in)
                    Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
