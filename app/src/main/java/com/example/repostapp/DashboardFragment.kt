package com.example.repostapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"

        fun newInstance(userId: String?, token: String?): DashboardFragment {
            val frag = DashboardFragment()
            frag.arguments = bundleOf(ARG_USER_ID to userId, ARG_TOKEN to token)
            return frag
        }
    }

    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PostAdapter(mutableListOf())
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_posts)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val token = arguments?.getString(ARG_TOKEN) ?: ""
        val userId = arguments?.getString(ARG_USER_ID) ?: ""
        if (token.isNotBlank() && userId.isNotBlank()) {
            fetchUsernameAndPosts(userId, token)
        }
    }

    private fun fetchUsernameAndPosts(userId: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    val username = if (resp.isSuccessful) {
                        try {
                            JSONObject(body ?: "{}").optJSONObject("data")?.optString("insta")
                        } catch (_: Exception) { null }
                    } else null
                    withContext(Dispatchers.Main) {
                        if (!username.isNullOrBlank()) {
                            fetchTodayPosts(token, username)
                        } else {
                            Toast.makeText(requireContext(), "Username IG tidak ditemukan", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchTodayPosts(token: String, username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "https://papiqo.com/api/insta/rapid-posts?username=$username&limit=10"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    withContext(Dispatchers.Main) {
                        if (resp.isSuccessful) {
                            val arr = try {
                                JSONObject(body ?: "{}").optJSONArray("data")
                            } catch (_: Exception) { JSONArray() }
                            val posts = mutableListOf<InstaPost>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                posts.add(
                                    InstaPost(
                                        id = obj.optString("id"),
                                        caption = obj.optString("caption"),
                                        imageUrl = obj.optString("thumbnail"),
                                        createdAt = obj.optString("created_at")
                                    )
                                )
                            }
                            adapter.setData(posts)
                        } else {
                            Toast.makeText(requireContext(), "Gagal mengambil konten", Toast.LENGTH_SHORT).show()
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

