package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.bumptech.glide.Glide
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
        val loginContainer: View = view.findViewById(R.id.login_container)
        val profileContainer: View = view.findViewById(R.id.profile_container)
        val usernameInput: TextInputEditText = view.findViewById(R.id.input_tiktok_username)
        val passwordInput: TextInputEditText = view.findViewById(R.id.input_tiktok_password)
        val statusView: TextView = view.findViewById(R.id.text_tiktok_status)
        val loginButton: MaterialButton = view.findViewById(R.id.button_tiktok_login)
        val avatarView: ImageView = view.findViewById(R.id.image_tiktok_avatar)
        val usernameView: TextView = view.findViewById(R.id.text_tiktok_username)
        val followerView: TextView = view.findViewById(R.id.stat_tiktok_followers)
        val followingView: TextView = view.findViewById(R.id.stat_tiktok_following)
        val logoutButton: MaterialButton = view.findViewById(R.id.button_tiktok_logout)

        loginButton.setOnClickListener {
            val user = usernameInput.text?.toString()?.trim().orEmpty()
            val pass = passwordInput.text?.toString()?.trim().orEmpty()
            if (user.isNotBlank() && pass.isNotBlank()) {
                performLogin(
                    user,
                    pass,
                    statusView,
                    loginContainer,
                    profileContainer,
                    avatarView,
                    usernameView,
                    followerView,
                    followingView
                )
            } else {
                Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }

        logoutButton.setOnClickListener {
            TiktokSessionManager.clear(requireContext())
            loginContainer.visibility = View.VISIBLE
            profileContainer.visibility = View.GONE
            statusView.text = getString(R.string.not_logged_in)
        }

        TiktokSessionManager.loadProfile(requireContext())?.let {
            displayProfile(it, loginContainer, profileContainer, avatarView, usernameView, followerView, followingView)
        }
    }

    private fun performLogin(
        user: String,
        pass: String,
        statusView: TextView,
        loginContainer: View,
        profileContainer: View,
        avatarView: ImageView,
        usernameView: TextView,
        followerView: TextView,
        followingView: TextView,
    ) {
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
                            TiktokSessionManager.saveProfile(requireContext(), data)
                            displayProfile(
                                data,
                                loginContainer,
                                profileContainer,
                                avatarView,
                                usernameView,
                                followerView,
                                followingView
                            )
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

    private fun displayProfile(
        data: JSONObject,
        loginContainer: View,
        profileContainer: View,
        avatarView: ImageView,
        usernameView: TextView,
        followerView: TextView,
        followingView: TextView,
    ) {
        val user = data.optJSONObject("user")
        val stats = data.optJSONObject("stats")
        val uid = user?.optString("uniqueId") ?: ""
        val avatar = user?.optString("avatarThumb") ?: ""
        usernameView.text = "@$uid"
        followerView.text = (stats?.optInt("followerCount") ?: 0).toString()
        followingView.text = (stats?.optInt("followingCount") ?: 0).toString()
        if (avatar.isNotBlank()) {
            Glide.with(this).load(avatar).circleCrop().into(avatarView)
        } else {
            avatarView.setImageDrawable(null)
        }
        loginContainer.visibility = View.GONE
        profileContainer.visibility = View.VISIBLE
    }
}
