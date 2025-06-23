package com.example.repostapp

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var progressBar: ProgressBar
    private val downloadedIds = mutableSetOf<String>()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), "Izin penyimpanan diperlukan", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("downloads", Context.MODE_PRIVATE)
        downloadedIds.addAll(prefs.getStringSet("ids", emptySet()) ?: emptySet())

        adapter = PostAdapter(mutableListOf()) { post ->
            handlePostClicked(post)
        }
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_posts)
        progressBar = view.findViewById(R.id.progress_loading)
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
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), "Gagal memuat data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
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
                                    if (created.contains("T")) {
                                        java.time.OffsetDateTime.parse(created)
                                            .atZoneSameInstant(java.time.ZoneId.systemDefault())
                                            .toLocalDate()
                                    } else {
                                        java.time.LocalDateTime.parse(created, formatter).toLocalDate()
                                    }
                                } catch (_: Exception) { null }
                                if (createdDate == today) {
                                    val id = obj.optString("shortcode")
                                    posts.add(
                                        InstaPost(
                                            id = id,
                                            caption = obj.optString("caption"),
                                            imageUrl = obj.optString("image_url")
                                                .ifBlank { obj.optString("thumbnail_url") },
                                            createdAt = created,
                                            isVideo = obj.optBoolean("is_video"),
                                            videoUrl = obj.optString("video_url"),
                                            sourceUrl = obj.optString("source_url"),
                                            downloaded = downloadedIds.contains(id)
                                        )
                                    )
                                }
                            }
                            adapter.setData(posts)
                            progressBar.visibility = View.GONE
                        } else {
                            progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), "Gagal mengambil konten", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Gagal terhubung ke server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handlePostClicked(post: InstaPost) {
        if (!downloadedIds.contains(post.id)) {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage("Download konten ini?")
                .setPositiveButton("Download") { _, _ ->
                    requestStorageAndDownload(post)
                }
                .setNegativeButton("Batal", null)
                .create()
            dialog.show()
        } else {
            val options = arrayOf("Share", "Lapor")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> sharePost(post)
                        1 -> startActivity(Intent(requireContext(), ReportActivity::class.java))
                    }
                }
                .show()
        }
    }

    private fun requestStorageAndDownload(post: InstaPost) {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(permission)
            return
        }
        downloadPost(post)
    }

    private fun downloadPost(post: InstaPost) {
        val url = if (post.isVideo) post.videoUrl else post.sourceUrl ?: post.imageUrl
        if (url.isNullOrBlank()) return
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val request = DownloadManager.Request(Uri.parse(url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setTitle(fileName)
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        downloadedIds.add(post.id)
        post.downloaded = true
        val prefs = requireContext().getSharedPreferences("downloads", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("ids", downloadedIds).apply()
        adapter.notifyDataSetChanged()
    }

    private fun sharePost(post: InstaPost) {
        val url = if (post.isVideo) post.videoUrl else post.sourceUrl ?: post.imageUrl
        if (url.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = if (post.isVideo) "video/*" else "image/*"
        intent.putExtra(Intent.EXTRA_TEXT, url)
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("caption", post.caption ?: ""))
        startActivity(Intent.createChooser(intent, "Share via"))
    }
}

