package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
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

class SpecialTaskFragment : Fragment(R.layout.fragment_special_task) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"

        fun newInstance(userId: String?, token: String?): SpecialTaskFragment {
            val frag = SpecialTaskFragment()
            frag.arguments = bundleOf(ARG_USER_ID to userId, ARG_TOKEN to token)
            return frag
        }
    }

    private lateinit var adapter: PostAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PostAdapter(mutableListOf()) { }
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_posts_khusus)
        progressBar = view.findViewById(R.id.progress_loading_khusus)
        emptyView = view.findViewById(R.id.text_empty_khusus)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        val token = arguments?.getString(ARG_TOKEN) ?: ""
        val userId = arguments?.getString(ARG_USER_ID) ?: ""
        if (token.isNotBlank() && userId.isNotBlank()) {
            progressBar.visibility = View.VISIBLE
            fetchClientAndPosts(userId, token)
        }
    }

    private fun fetchClientAndPosts(userId: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val req = Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    val clientId = if (resp.isSuccessful) {
                        try {
                            JSONObject(body ?: "{}").optJSONObject("data")?.optString("client_id") ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                    withContext(Dispatchers.Main) {
                        if (clientId.isNotBlank()) {
                            fetchSpecialPosts(token, clientId)
                        } else {
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), "Gagal memuat data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchSpecialPosts(token: String, clientId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val url = "${BuildConfig.API_BASE_URL}/api/insta/posts-khusus?client_id=$clientId"
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    withContext(Dispatchers.Main) {
                        emptyView.visibility = View.GONE
                        if (resp.isSuccessful) {
                            val arr = try { JSONObject(body ?: "{}").optJSONArray("data") } catch (_: Exception) { JSONArray() }
                            val posts = mutableListOf<InstaPost>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                posts.add(
                                    InstaPost(
                                        id = obj.optString("shortcode"),
                                        caption = obj.optString("caption"),
                                        imageUrl = obj.optString("image_url"),
                                        createdAt = obj.optString("created_at"),
                                        taskNumber = i + 1,
                                        isVideo = obj.optBoolean("is_video"),
                                        videoUrl = obj.optString("video_url"),
                                        sourceUrl = obj.optString("source_url"),
                                        isCarousel = false,
                                        carouselImages = emptyList()
                                    )
                                )
                            }
                            adapter.setData(posts)
                            emptyView.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                            progressBar.visibility = View.GONE
                        } else {
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), "Gagal mengambil data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

