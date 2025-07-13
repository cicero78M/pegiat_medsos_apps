package com.cicero.repostapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.cicero.repostapp.service.InstagramPostService
import com.cicero.repostapp.service.TwitterPostService
import com.cicero.repostapp.util.AccessibilityUtils

class AutopostFragment : Fragment(R.layout.fragment_autopost) {

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_TOKEN = "token"
        private const val PAGE_INDEX = 2

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
    private var currentPost: InstaPost? = null
    private var receiver: BroadcastReceiver? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        console = view.findViewById(R.id.text_console)
        view.findViewById<Button>(R.id.button_start_autopost).setOnClickListener {
            startAutopost()
        }
        token = arguments?.getString(ARG_TOKEN) ?: ""
        userId = arguments?.getString(ARG_USER_ID) ?: ""

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    InstagramPostService.ACTION_UPLOAD_FINISHED -> {
                        log("Instagram upload finished")
                        currentPost?.let { shareToTwitter(it) }
                    }
                    TwitterPostService.ACTION_UPLOAD_FINISHED -> {
                        log("Twitter upload finished")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(InstagramPostService.ACTION_UPLOAD_FINISHED)
            addAction(TwitterPostService.ACTION_UPLOAD_FINISHED)
        }
        requireContext().registerReceiver(receiver, filter)

        collectPageChanges()
    }

    private fun log(message: String) {
        console.append("$message\n")
    }

    private fun ensureAccessibilityServices() {
        val services = listOf(
            InstagramPostService::class.java,
            TwitterPostService::class.java
        )
        val enabled = services.all {
            AccessibilityUtils.isServiceEnabled(requireContext(), it)
        }
        if (!enabled) {
            Toast.makeText(requireContext(), getString(R.string.enable_accessibility_service), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun collectPageChanges() {
        val vp = activity?.findViewById<ViewPager2>(R.id.view_pager) ?: return
        pageChangeFlow(vp)
            .onEach { pos -> if (pos == PAGE_INDEX) startAutopost() }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun pageChangeFlow(viewPager: ViewPager2) = callbackFlow<Int> {
        val cb = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                trySend(position).isSuccess
            }
        }
        viewPager.registerOnPageChangeCallback(cb)
        awaitClose { viewPager.unregisterOnPageChangeCallback(cb) }
    }

    private fun startAutopost() {
        if (token.isBlank() || userId.isBlank()) {
            Toast.makeText(requireContext(), "Token/UserId kosong", Toast.LENGTH_SHORT).show()
            return
        }
        ensureAccessibilityServices()
        console.text = ""
        lifecycleScope.launch {
            val posts = fetchPosts()
            if (posts.isEmpty()) {
                log("Tidak ada konten")
                return@launch
            }
            val post = posts.first()
            currentPost = post
            log("Proses ${post.id}")
            if (!checkIfFileExists(post)) {
                log("Downloading konten ...")
                downloadPost(post)
            } else {
                log("Konten sudah diunduh")
            }
            copyCaption(post.caption)
            log("Membuka Instagram")
            shareToInstagram(post)
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
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            val today = LocalDate.now()
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val created = obj.optString("created_at")
                                val createdDate = try {
                                    if (created.contains("T")) {
                                        OffsetDateTime.parse(created)
                                            .atZoneSameInstant(ZoneId.systemDefault())
                                            .toLocalDate()
                                    } else {
                                        LocalDateTime.parse(created, formatter).toLocalDate()
                                    }
                                } catch (_: Exception) { null }
                                if (createdDate == today) {
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
        ensureAccessibilityServices()
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
        if (!post.caption.isNullOrBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, post.caption)
        }
        intent.setPackage("com.instagram.android")
        intent.setClassName(
            "com.instagram.android",
            "com.instagram.share.handleractivity.ShareHandlerActivity"
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent.createChooser(intent, "Share via"))
        }
    }

    private fun shareToTwitter(post: InstaPost) {
        ensureAccessibilityServices()
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        val file = if (!post.localPath.isNullOrBlank()) {
            File(post.localPath!!)
        } else {
            File(dir, fileName)
        }
        val caption = post.caption ?: ""
        val tweetText = if (caption.length > 240) caption.substring(0, 210) else caption
        val leftover = if (caption.length > 240) caption.substring(210) else ""
        copyCaption(tweetText)
        TwitterPostService.replyText = leftover

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = if (post.isVideo) "video/*" else "image/*"
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(requireContext(), requireContext().packageName + ".fileprovider", file)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (tweetText.isNotBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, tweetText)
        }
        intent.setPackage("com.twitter.android")
        intent.setClassName(
            "com.twitter.android",
            "com.twitter.composer.ComposerActivity"
        )
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

    override fun onDestroyView() {
        receiver?.let { requireContext().unregisterReceiver(it) }
        receiver = null
        super.onDestroyView()
    }
}
