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

        val userId = arguments?.getString(ARG_USER_ID) ?: ""
        val token = arguments?.getString(ARG_TOKEN) ?: ""
        if (userId.isNotBlank() && token.isNotBlank()) {
            fetchTodayPosts(userId, token)
        }
    }

    private fun fetchTodayPosts(userId: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val profileReq = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(profileReq).execute().use { resp ->
                    val body = resp.body?.string()
                    if (resp.isSuccessful) {
                        val data = try {
                            JSONObject(body ?: "{}").optJSONObject("data")
                        } catch (_: Exception) { null }
                        val username = data?.optString("insta") ?: ""
                        if (username.isNotBlank()) {
                            fetchPostsByUsername(username, token)
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Username IG tidak ditemukan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
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

    private suspend fun fetchPostsByUsername(username: String, token: String) {
        val client = OkHttpClient()
        val url = "https://papiqo.com/api/insta/rapid-posts?username=$username&limit=20"
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
                        val today = LocalDate.now().toString()
                        val posts = mutableListOf<InstaPost>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val created = obj.optString("created_at")
                            if (created.startsWith(today)) {
                                posts.add(
                                    InstaPost(
                                        id = obj.optString("id"),
                                        caption = obj.optString("caption"),
                                        createdAt = created
                                    )
                                )
                            }
                        }
                        adapter.setData(posts)
                    } else {
                        Toast.makeText(requireContext(), "Gagal mengambil posting", Toast.LENGTH_SHORT).show()
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

