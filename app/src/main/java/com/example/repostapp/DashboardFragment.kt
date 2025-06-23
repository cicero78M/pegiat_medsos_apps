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
            fetchClientAndPosts(userId, token)
        }
    }

    private fun fetchClientAndPosts(userId: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    val clientId = if (resp.isSuccessful) {
                        try {
                            JSONObject(body ?: "{}")
                                .optJSONObject("data")
                                ?.optString("client_id") ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                    withContext(Dispatchers.Main) {
                        if (clientId.isNotBlank()) {
                            fetchPosts(token, clientId)
                        } else {
                            Toast.makeText(requireContext(), "Gagal memuat data", Toast.LENGTH_SHORT).show()
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

    private fun fetchPosts(token: String, clientId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "https://papiqo.com/api/insta/posts?client_id=$clientId"
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
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            val today = java.time.LocalDate.now()
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val created = obj.optString("created_at")
                                val createdDate = try {
                                    java.time.LocalDateTime.parse(created, formatter).toLocalDate()
                                } catch (_: Exception) { null }
                                if (createdDate == today) {
                                    posts.add(
                                        InstaPost(
                                            id = obj.optString("shortcode"),
                                            caption = obj.optString("caption"),
                                            imageUrl = obj.optString("image_url")
                                                .ifBlank { obj.optString("thumbnail_url") },
                                            createdAt = created
                                        )
                                    )
                                }
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

