package com.cicero.repostapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AutopostFragment : Fragment(R.layout.fragment_autopost) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"

        fun newInstance(userId: String?, token: String?): AutopostFragment {
            val frag = AutopostFragment()
            val args = Bundle()
            args.putString(ARG_USER_ID, userId)
            args.putString(ARG_TOKEN, token)
            frag.arguments = args
            return frag
        }
    }

    private lateinit var console: TextView
    private var token: String = ""
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        console = view.findViewById(R.id.text_console)
        view.findViewById<Button>(R.id.button_start_autopost).setOnClickListener {
            startAutopost()
        }
        token = arguments?.getString(ARG_TOKEN) ?: ""
        userId = arguments?.getString(ARG_USER_ID) ?: ""
    }

    private fun log(message: String) {
        console.append("$message\n")
    }

    private fun startAutopost() {
        if (token.isBlank() || userId.isBlank()) {
            Toast.makeText(requireContext(), "Token/UserId kosong", Toast.LENGTH_SHORT).show()
            return
        }
        console.text = ""
        lifecycleScope.launch {
            val posts = fetchPosts() // get posts from server
            if (posts.isEmpty()) {
                log("Tidak ada konten")
                return@launch
            }
            log("Memulai autopost ${posts.size} konten")
            for (post in posts) {
                log("Proses ${post.id}")
                if (!checkIfFileExists(post)) {
                    log("Downloading konten ...")
                    downloadPost(post)
                    delay(5000)
                } else {
                    log("Konten sudah diunduh")
                }
                delay(5000)
                copyCaption(post.caption)
                log("Membuka Instagram")
                shareToInstagram(post)
                delay(5000)
                log("Silakan selesaikan posting, lalu salin link hasilnya")
                // Placeholder to wait for user
                delay(15000)
            }
            log("Autopost selesai")
        }
    }

    private suspend fun fetchPosts(): List<InstaPost> {
        val posts = mutableListOf<InstaPost>()
        val client = OkHttpClient()
        return withContext(Dispatchers.IO) {
            try {
                val userReq = Request.Builder()
                    .url("https://papiqo.com/api/users/$userId")
                    .header("Authorization", "Bearer $token")
                    .build()
                val clientId = client.newCall(userReq).execute().use { resp ->
                    val body = resp.body?.string()
                    if (resp.isSuccessful) {
                        try {
                            JSONObject(body ?: "{}")
                                .optJSONObject("data")
                                ?.optString("client_id") ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                }
                if (clientId.isNotBlank()) {
                    val postsReq = Request.Builder()
                        .url("https://papiqo.com/api/insta/posts?client_id=$clientId")
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(postsReq).execute().use { resp ->
                        val body = resp.body?.string()
                        if (resp.isSuccessful) {
                            val arr = try {
                                JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray()
                            } catch (_: Exception) { JSONArray() }
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                posts.add(
                                    InstaPost(
                                        id = obj.optString("shortcode"),
                                        caption = obj.optString("caption"),
                                        imageUrl = obj.optString("image_url"),
                                        createdAt = obj.optString("created_at"),
                                        isVideo = obj.optBoolean("is_video"),
                                        videoUrl = obj.optString("video_url"),
                                        sourceUrl = obj.optString("source_url"),
                                    )
                                )
                            }
                        }
                    }
                }
                posts
            } catch (_: Exception) {
                posts
            }
        }
    }

    private fun copyCaption(caption: String?) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("caption", caption ?: "")
        clipboard.setPrimaryClip(clip)
        log("Caption disalin")
    }

    private fun shareToInstagram(post: InstaPost) {
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        val file = if (!post.localPath.isNullOrBlank()) {
            File(post.localPath!!)
        } else {
            File(dir, fileName)
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = if (post.isVideo) "video/*" else "image/*"
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".fileprovider", file)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        intent.setPackage("com.instagram.android")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent.createChooser(intent, "Share via"))
        }
    }

    private fun checkIfFileExists(post: InstaPost): Boolean {
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        val file = if (!post.localPath.isNullOrBlank()) {
            File(post.localPath!!)
        } else {
            File(dir, fileName)
        }
        return file.exists()
    }

    private suspend fun downloadPost(post: InstaPost) {
        val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
        if (url.isNullOrBlank()) return
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    resp.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                post.localPath = file.absolutePath
                log("Download selesai")
            } catch (e: Exception) {
                log("Gagal download: ${e.message}")
            }
        }
    }
}
