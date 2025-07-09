package com.cicero.repostapp

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
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
    private lateinit var emptyView: android.widget.TextView
    private val downloadedIds = mutableSetOf<String>()
    private val reportedIds = mutableSetOf<String>()
    private var token: String = ""
    private var userId: String = ""


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("downloads", Context.MODE_PRIVATE)
        downloadedIds.addAll(prefs.getStringSet("ids", emptySet()) ?: emptySet())

        adapter = PostAdapter(mutableListOf()) { post ->
            handlePostClicked(post)
        }
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_posts)
        progressBar = view.findViewById(R.id.progress_loading)
        emptyView = view.findViewById(R.id.text_empty)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        token = arguments?.getString(ARG_TOKEN) ?: ""
        userId = arguments?.getString(ARG_USER_ID) ?: ""
        if (token.isNotBlank() && userId.isNotBlank()) {
            progressBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                reportedIds.clear()
                reportedIds.addAll(fetchReportedShortcodes(token, userId))
                withContext(Dispatchers.Main) {
                    fetchClientAndPosts(userId, token)
                }
            }
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
                        emptyView.visibility = View.GONE
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
                                            downloaded = downloadedIds.contains(id),
                                            reported = reportedIds.contains(id)
                                        )
                                    )
                                }
                            }
                            adapter.setData(posts)
                            emptyView.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
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
        ensureDownloadDir {
            proceedWithPost(post)
        }
    }

    private fun proceedWithPost(post: InstaPost) {
        val exists = checkIfFileExists(post)

        if (exists) {
            if (!downloadedIds.contains(post.id)) {
                downloadedIds.add(post.id)
                post.downloaded = true
                val prefs = requireContext().getSharedPreferences("downloads", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("ids", downloadedIds).apply()
                adapter.notifyDataSetChanged()
            }
            showShareDialog(post)
            return
        }

        if (downloadedIds.contains(post.id)) {
            showShareDialog(post)
        } else {
            requestStorageAndDownload(post)
        }
    }

    private fun ensureDownloadDir(onReady: () -> Unit) {
        val dir = java.io.File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        if (dir.exists()) {
            onReady()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage("Folder CiceroReposterApp tidak ditemukan. Klik OK untuk membuat folder.")
            .setPositiveButton("OK") { _, _ ->
                dir.mkdirs()
                if (!dir.exists()) {
                    Toast.makeText(requireContext(), "Gagal membuat folder", Toast.LENGTH_SHORT).show()
                } else {
                    onReady()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun checkIfFileExists(post: InstaPost): Boolean {
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val dir = java.io.File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        val file = if (!post.localPath.isNullOrBlank()) {
            java.io.File(post.localPath!!)
        } else {
            java.io.File(dir, fileName)
        }
        return file.exists()
    }

    private fun requestStorageAndDownload(post: InstaPost) {
        if (checkIfFileExists(post)) {
            if (!downloadedIds.contains(post.id)) {
                downloadedIds.add(post.id)
                post.downloaded = true
                val prefs = requireContext().getSharedPreferences("downloads", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("ids", downloadedIds).apply()
                adapter.notifyDataSetChanged()
            }
            showShareDialog(post)
            return
        }

        downloadPost(post)
    }

    private fun downloadPost(post: InstaPost) {
        val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
        if (url.isNullOrBlank()) return
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        requireActivity().window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string()
                        Log.e(
                            "DashboardFragment",
                            "HTTP ${resp.code} ${resp.message} when downloading $url. Body: $errBody"
                        )
                        throw java.io.IOException("HTTP ${resp.code} ${resp.message}")
                    }
                    val body = resp.body ?: return@use
                    val dir = java.io.File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                    val file = java.io.File(dir, fileName)
                    file.outputStream().use { out ->
                        val total = body.contentLength()
                        var downloaded = 0L
                        val buf = ByteArray(8 * 1024)
                        var read: Int
                        while (body.byteStream().read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                            withContext(Dispatchers.Main) {
                                progressBar.isIndeterminate = false
                                progressBar.progress = progress
                            }
                        }
                    }
                    post.localPath = file.absolutePath
                }
                withContext(Dispatchers.Main) {
                    downloadedIds.add(post.id)
                    post.downloaded = true
                    val prefs = requireContext().getSharedPreferences("downloads", Context.MODE_PRIVATE)
                    prefs.edit().putStringSet("ids", downloadedIds).apply()
                    progressBar.visibility = View.GONE
                    progressBar.isIndeterminate = true
                    requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e(
                    "DashboardFragment",
                    "Failed to download $url to $fileName: ${e.message}",
                    e
                )
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    progressBar.isIndeterminate = true
                    requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    val msg = "Gagal download: ${e.message}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sharePost(post: InstaPost) {
        val fileName = post.id + if (post.isVideo) ".mp4" else ".jpg"
        val dir = java.io.File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = if (!post.localPath.isNullOrBlank()) {
            java.io.File(post.localPath!!)
        } else {
            java.io.File(dir, fileName)
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = if (post.isVideo) "video/*" else "image/*"
        if (file.exists()) {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                file
            )
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
            if (!url.isNullOrBlank()) intent.putExtra(Intent.EXTRA_TEXT, url)
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("caption", post.caption ?: ""))
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun showShareDialog(post: InstaPost) {
        val opts = if (post.reported) {
            arrayOf("Share", "Kirim Link", "Laporan WhatsApp")
        } else {
            arrayOf("Share", "Kirim Link")
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> sharePost(post)
                    1 -> {
                        val intent = Intent(requireContext(), ReportActivity::class.java).apply {
                            putExtra(ReportActivity.EXTRA_IMAGE_URL, post.imageUrl)
                            putExtra(ReportActivity.EXTRA_CAPTION, post.caption)
                            putExtra(ReportActivity.EXTRA_SHORTCODE, post.id)
                        }
                        startActivity(intent)
                    }
                    2 -> if (post.reported) {
                        shareReportViaWhatsApp(post.id)
                    }
                }
            }
            .show()
    }

    private fun shareShortcodeViaWhatsApp(shortcode: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://instagram.com/p/$shortcode")
            setPackage("com.whatsapp")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent.createChooser(intent, "Share via"))
        }
    }

    private fun shareReportViaWhatsApp(shortcode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val links = getExistingReport(shortcode)
            if (links != null) {
                withContext(Dispatchers.Main) {
                    shareViaWhatsApp(shortcode, links)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal mengambil laporan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getExistingReport(sc: String): Map<String, String?>? {
        if (token.isBlank()) return null
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/link-reports")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                val arr = try {
                    JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray()
                } catch (_: Exception) { JSONArray() }
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("shortcode") == sc && o.optString("user_id") == userId) {
                        return mapOf(
                            "instagram" to o.optString("instagram_link").takeIf { it.isNotBlank() },
                            "twitter" to o.optString("twitter_link").takeIf { it.isNotBlank() },
                            "tiktok" to o.optString("tiktok_link").takeIf { it.isNotBlank() }
                        )
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shareViaWhatsApp(shortcode: String, links: Map<String, String?>) {
        val locale = java.util.Locale("id", "ID")
        val today = java.time.LocalDate.now()
        val day = today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", locale))
        val dateStr = today.format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", locale))
        val prefsAuth = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        val rank = prefsAuth.getString("rank", "") ?: ""
        val name = prefsAuth.getString("name", "") ?: ""
        val satfung = prefsAuth.getString("satfung", "") ?: ""
        val nrp = prefsAuth.getString("userId", userId) ?: userId
        val userInfo = """
            Tambahkan data nama : $rank $name
            NRP / NIP : $nrp
            Satfung : $satfung
        """.trimIndent()

        val message = """
            Mohon ijin, Mengirimkan Laporan repost konten,

            Hari : $day,
            Tanggal : $dateStr

            $userInfo

            dari Source Link Konten Instagram berikut :
            https://instagram.com/p/$shortcode

            Laporan Link Pelaksanaan Sebagai Berikut :
            1. ${links["instagram"] ?: "-"},
            2. ${links["twitter"] ?: "-"},
            3. ${links["tiktok"] ?: "-"}
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent.createChooser(intent, "Share via"))
        }
    }

    private suspend fun fetchReportedShortcodes(token: String, userId: String): Set<String> {
        if (token.isBlank() || userId.isBlank()) return emptySet()
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/link-reports")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptySet()
                val body = resp.body?.string()
                val arr = try {
                    JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray()
                } catch (_: Exception) { JSONArray() }
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val sc = obj.optString("shortcode")
                    val uid = obj.optString("user_id")
                    if (sc.isNotBlank() && uid == userId) set.add(sc)
                }
                set
            }
        } catch (_: Exception) {
            emptySet()
        }
    }
}

