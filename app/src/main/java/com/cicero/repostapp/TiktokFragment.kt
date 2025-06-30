package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class TiktokFragment : Fragment(R.layout.fragment_tiktok) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loginContainer: View = view.findViewById(R.id.login_container)
        val profileContainer: View = view.findViewById(R.id.profile_container)
        val statusView: TextView = view.findViewById(R.id.text_tiktok_status)
        val loginButton: MaterialButton = view.findViewById(R.id.button_tiktok_login)
        val logoutButton: MaterialButton = view.findViewById(R.id.button_tiktok_logout)
        val usernameInput: TextInputEditText = view.findViewById(R.id.input_tiktok_username)
        val avatar: android.widget.ImageView = view.findViewById(R.id.image_tiktok_avatar)
        val usernameView: TextView = view.findViewById(R.id.text_tiktok_username)

        loginButton.setOnClickListener {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            if (username.isEmpty()) {
                Toast.makeText(requireContext(), "Masukkan username", Toast.LENGTH_SHORT).show()
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val data = TikwmApi.fetchUser(username)
                    if (data != null) {
                        TiktokSessionManager.saveProfile(requireContext(), data)
                        usernameView.text = data.optString("uniqueId")
                        val avatarUrl = data.optString("avatarLarger", data.optString("avatarThumb"))
                        if (avatarUrl.isNotBlank()) {
                            Glide.with(this@TiktokFragment).load(avatarUrl).into(avatar)
                        }
                        statusView.text = getString(R.string.login)
                        displayProfile(loginContainer, profileContainer)
                    } else {
                        Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        logoutButton.setOnClickListener {
            TiktokSessionManager.clear(requireContext())
            loginContainer.visibility = View.VISIBLE
            profileContainer.visibility = View.GONE
            statusView.text = getString(R.string.not_logged_in)
        }

        TiktokSessionManager.loadProfile(requireContext())?.let { profile ->
            usernameView.text = profile.optString("uniqueId")
            val avatarUrl = profile.optString("avatarLarger", profile.optString("avatarThumb"))
            if (avatarUrl.isNotBlank()) {
                Glide.with(this@TiktokFragment).load(avatarUrl).into(avatar)
            }
            displayProfile(loginContainer, profileContainer)
        }
    }

    private fun displayProfile(loginContainer: View, profileContainer: View) {
        loginContainer.visibility = View.GONE
        profileContainer.visibility = View.VISIBLE
    }
}
