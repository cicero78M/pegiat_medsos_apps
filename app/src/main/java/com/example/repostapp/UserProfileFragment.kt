package com.example.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.content.Intent
import android.content.Context
import android.widget.ImageView
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UserProfileFragment : Fragment(R.layout.activity_profile) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"

        fun newInstance(userId: String?, token: String?): UserProfileFragment {
            val fragment = UserProfileFragment()
            fragment.arguments = bundleOf(ARG_USER_ID to userId, ARG_TOKEN to token)
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = arguments?.getString(ARG_USER_ID) ?: ""
        val token = arguments?.getString(ARG_TOKEN) ?: ""
        view.findViewById<Button>(R.id.button_logout).setOnClickListener {
            val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            prefs.edit { clear() }
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }
        if (userId.isNotBlank() && token.isNotBlank()) {
            fetchProfile(userId, token, view)
        }
    }

    private fun fetchProfile(userId: String, token: String, rootView: View) {
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
                            rootView.findViewById<TextView>(R.id.text_username).text =
                                "@" + insta
                            rootView.findViewById<TextView>(R.id.text_name).text =
                                (data?.optString("title") ?: "") + " " + (data?.optString("nama") ?: "")
                            rootView.findViewById<TextView>(R.id.text_nrp).text =
                                (data?.optString("user_id") ?: userId)
                            rootView.findViewById<TextView>(R.id.text_client_id).text =
                                (data?.optString("client_id") ?: "")
                            rootView.findViewById<TextView>(R.id.text_satfung).text =
                                (data?.optString("divisi") ?: "")
                            rootView.findViewById<TextView>(R.id.text_jabatan).text =
                                (data?.optString("jabatan") ?: "")
                            rootView.findViewById<TextView>(R.id.text_tiktok).text =
                                (data?.optString("tiktok") ?: "")
                            val statusText = data?.optString("status") ?: ""
                            rootView.findViewById<TextView>(R.id.text_status).text = statusText

                            val avatarUrl = data?.optString("profile_pic_url") ?: ""
                            Glide.with(this@UserProfileFragment)
                                .load(avatarUrl)
                                .placeholder(R.drawable.profile_avatar_placeholder)
                                .error(R.drawable.profile_avatar_placeholder)
                                .into(rootView.findViewById(R.id.image_avatar))

                            val statusImage = if (statusText.equals("true", true)) {
                                R.drawable.ic_status_true
                            } else {
                                R.drawable.ic_status_false
                            }
                            rootView.findViewById<ImageView>(R.id.image_status).setImageResource(statusImage)
                            fetchStats(token, insta, rootView)
                        } else {
                            Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchStats(token: String, username: String, rootView: View) {
        if (username.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val stats = getStatsFromDb(token, username) ?: run {
                fetchAndStoreStats(token, username)
                getStatsFromDb(token, username)
            }
            withContext(Dispatchers.Main) {
                rootView.findViewById<TextView>(R.id.stat_posts).text =
                    (stats?.optInt("post_count") ?: 0).toString()
                rootView.findViewById<TextView>(R.id.stat_followers).text =
                    (stats?.optInt("follower_count") ?: 0).toString()
                rootView.findViewById<TextView>(R.id.stat_following).text =
                    (stats?.optInt("following_count") ?: 0).toString()
            }
        }
    }

    private suspend fun getStatsFromDb(token: String, username: String): JSONObject? {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/insta/profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                val obj = JSONObject(body ?: "{}")
                obj.optJSONObject("data") ?: obj
            }
        } catch (_: Exception) {
            null
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
