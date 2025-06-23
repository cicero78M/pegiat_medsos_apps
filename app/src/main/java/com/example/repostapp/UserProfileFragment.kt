package com.example.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
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
                            rootView.findViewById<TextView>(R.id.text_index).text =
                                "Urutan: " + (data?.optString("index") ?: "")
                            rootView.findViewById<TextView>(R.id.text_client_id).text =
                                "Client ID: " + (data?.optString("client_id") ?: "")
                            rootView.findViewById<TextView>(R.id.text_name).text =
                                "Nama: " + (data?.optString("nama") ?: "")
                            rootView.findViewById<TextView>(R.id.text_rank).text =
                                "Pangkat: " + (data?.optString("title") ?: "")
                            rootView.findViewById<TextView>(R.id.text_nrp).text =
                                "NRP: " + (data?.optString("user_id") ?: userId)
                            rootView.findViewById<TextView>(R.id.text_satfung).text =
                                "Satfung: " + (data?.optString("divisi") ?: "")
                            rootView.findViewById<TextView>(R.id.text_jabatan).text =
                                "Jabatan: " + (data?.optString("jabatan") ?: "")
                            rootView.findViewById<TextView>(R.id.text_ig).text =
                                "Username IG: " + (data?.optString("insta") ?: "")
                            rootView.findViewById<TextView>(R.id.text_tiktok).text =
                                "Username TikTok: " + (data?.optString("tiktok") ?: "")
                            rootView.findViewById<TextView>(R.id.text_status).text =
                                "Status: " + (data?.optString("status") ?: "")
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
}
