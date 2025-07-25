package com.cicero.repostapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
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
        if (userId.isNotBlank() && token.isNotBlank()) {
            fetchProfile(userId, token, view)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchProfile(userId: String, token: String, rootView: View) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
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
                            val rank = data?.optString("title") ?: ""
                            val name = data?.optString("nama") ?: ""
                            val satfung = data?.optString("divisi") ?: ""
                            val nrp = data?.optString("user_id") ?: userId

                            val authPrefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
                            authPrefs.edit()
                                .putString("rank", rank)
                                .putString("name", name)
                                .putString("satfung", satfung)
                                .apply()

                            rootView.findViewById<TextView>(R.id.text_username).text =
                                "@$insta"
                            rootView.findViewById<TextView>(R.id.text_name).text =
                                "$rank $name"
                            rootView.findViewById<TextView>(R.id.text_nrp).text =
                                nrp
                            rootView.findViewById<TextView>(R.id.text_client_id).text =
                                (data?.optString("client_id") ?: "")
                            rootView.findViewById<TextView>(R.id.text_satfung).text =
                                satfung
                            rootView.findViewById<TextView>(R.id.text_jabatan).text =
                                (data?.optString("jabatan") ?: "")
                            rootView.findViewById<TextView>(R.id.text_tiktok).text =
                                (data?.optString("tiktok") ?: "")
                            val statusText = data?.optString("status") ?: ""


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
            var (stats, _) = getStatsFromDb(token, username)
            if (stats == null) {
                fetchAndStoreStats(token, username)
                val result = getStatsFromDb(token, username)
                stats = result.first
            }
            withContext(Dispatchers.Main) {
                rootView.findViewById<TextView>(R.id.stat_posts).text =
                    (stats?.optInt("post_count") ?: 0).toString()
                rootView.findViewById<TextView>(R.id.stat_followers).text =
                    (stats?.optInt("follower_count") ?: 0).toString()
                rootView.findViewById<TextView>(R.id.stat_following).text =
                    (stats?.optInt("following_count") ?: 0).toString()
                val avatarUrl = stats?.optString("profile_pic_url") ?: ""
                val fullAvatarUrl = if (avatarUrl.startsWith("http"))
                    avatarUrl else "${BuildConfig.API_BASE_URL}$avatarUrl"

                Glide.with(this@UserProfileFragment)
                    .load(fullAvatarUrl)
                    .placeholder(R.drawable.profile_avatar_placeholder)
                    .error(R.drawable.profile_avatar_placeholder)
                    .circleCrop()
                    .into(rootView.findViewById(R.id.image_avatar))

            }
        }
    }

    private fun getStatsFromDb(token: String, username: String): Pair<JSONObject?, String?> {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/insta/profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Pair(null, null)
                val body = resp.body?.string()
                val obj = JSONObject(body ?: "{}")
                Pair(obj.optJSONObject("data") ?: obj, body)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    private fun fetchAndStoreStats(token: String, username: String) {
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/insta/rapid-profile?username=$username")
            .header("Authorization", "Bearer $token")
            .build()
        try {
            client.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

}
