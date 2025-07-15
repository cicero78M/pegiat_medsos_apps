package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import java.io.File
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import twitter4j.TwitterFactory
import twitter4j.auth.AccessToken

class AutopostFragment : Fragment() {

    companion object {
        fun newInstance(): AutopostFragment = AutopostFragment()
    }

    private var igClient: IGClient? = null
    private var twitterToken: AccessToken? = null

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("response", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Tersalin", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun sessionFiles(): Pair<File, File> {
        val dir = requireContext().filesDir
        return Pair(File(dir, "igclient.ser"), File(dir, "cookie.ser"))
    }

    private fun twitterPrefs() = requireContext().getSharedPreferences("twitter_auth", android.content.Context.MODE_PRIVATE)

    private fun saveTwitterToken(token: AccessToken, profile: String?) {
        twitterPrefs().edit().apply {
            putString("token", token.token)
            putString("secret", token.tokenSecret)
            if (profile != null) putString("profile", profile)
        }.apply()
    }

    private fun loadTwitterToken(): Triple<AccessToken?, String?, Boolean> {
        val prefs = twitterPrefs()
        val t = prefs.getString("token", null)
        val s = prefs.getString("secret", null)
        val p = prefs.getString("profile", null)
        return if (t != null && s != null) Triple(AccessToken(t, s), p, true) else Triple(null, null, false)
    }

    private fun fbSessionFile(): File = File(requireContext().filesDir, "fb_session.txt")

    private fun saveFbSession(userId: String, cookie: String) {
        try {
            fbSessionFile().writeText("$userId\n$cookie")
        } catch (_: Exception) {
        }
    }

    private suspend fun loadFbSession(icon: ImageView, check: ImageView) {
        val file = fbSessionFile()
        if (!file.exists()) return
        try {
            val lines = file.readLines()
            if (lines.size < 2) return
            val userId = lines[0]
            val pic = "https://graph.facebook.com/$userId/picture?type=normal"
            withContext(Dispatchers.Main) {
                Glide.with(this@AutopostFragment)
                    .load(pic)
                    .circleCrop()
                    .into(icon)
                check.visibility = View.VISIBLE
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_autopost, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val icon = view.findViewById<ImageView>(R.id.instagram_icon)
        val check = view.findViewById<ImageView>(R.id.check_mark)
        val fbIcon = view.findViewById<ImageView>(R.id.facebook_icon)
        val fbCheck = view.findViewById<ImageView>(R.id.facebook_check)
        val twitterIcon = view.findViewById<ImageView>(R.id.twitter_icon)
        val twitterCheck = view.findViewById<ImageView>(R.id.twitter_check)
        val start = view.findViewById<Button>(R.id.button_start)

        // attempt to load saved session
        lifecycleScope.launch(Dispatchers.IO) {
            loadSavedSession(icon, check)
            loadFbSession(fbIcon, fbCheck)
            loadTwitterSession(twitterIcon, twitterCheck)
        }

        icon.setOnClickListener { showLoginDialog(icon, check) }
        fbIcon.setOnClickListener { showFbLoginDialog(fbIcon, fbCheck) }
        twitterIcon.setOnClickListener { launchTwitterLogin() }
        start.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { runAutopostWorkflow() }
        }
    }

    private fun showLoginDialog(icon: ImageView, check: ImageView) {
        val view = layoutInflater.inflate(R.layout.dialog_login, null)
        val userInput = view.findViewById<EditText>(R.id.edit_username)
        val passInput = view.findViewById<EditText>(R.id.edit_password)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Login") { _, _ ->
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (user.isBlank() || pass.isBlank()) {
                    Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    performLogin(user, pass, icon, check)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showFbLoginDialog(icon: ImageView, check: ImageView) {
        val view = layoutInflater.inflate(R.layout.dialog_login, null)
        val userInput = view.findViewById<EditText>(R.id.edit_username)
        val passInput = view.findViewById<EditText>(R.id.edit_password)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Login") { _, _ ->
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (user.isBlank() || pass.isBlank()) {
                    Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    performFbLogin(user, pass, icon, check)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performLogin(username: String, password: String, icon: ImageView, check: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val twoFactor = IGClient.Builder.LoginHandler { client, resp ->
                    val code = runBlocking { promptTwoFactorCode() }
                    if (code.isNullOrEmpty()) return@LoginHandler resp
                    IGChallengeUtils.resolveTwoFactor(client, resp) { code }
                }
                val checkpoint = IGClient.Builder.LoginHandler { client, resp ->
                    val code = runBlocking { promptCheckpointCode() }
                    if (code.isNullOrEmpty()) return@LoginHandler resp
                    IGChallengeUtils.resolveChallenge(client, resp) { code }
                }

                val client = IGClient.builder()
                    .username(username)
                    .password(password)
                    .onTwoFactor(twoFactor)
                    .onChallenge(checkpoint)
                    .login()

                igClient = client
                saveSession(client)
                val pic = client.selfProfile.profile_pic_url
                withContext(Dispatchers.Main) {
                    Glide.with(this@AutopostFragment)
                        .load(pic)
                        .circleCrop()
                        .into(icon)
                    check.visibility = View.VISIBLE
                }
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Gagal terhubung", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun performFbLogin(username: String, password: String, icon: ImageView, check: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .followRedirects(false)
                    .build()
                val body = "email=$username&pass=$password".toRequestBody("application/x-www-form-urlencoded".toMediaType())
                val req = Request.Builder()
                    .url("https://m.facebook.com/login.php?login_attempt=1")
                    .post(body)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val cookies = resp.headers("Set-Cookie")
                    val cookie = cookies.joinToString("; ")
                    val userId = cookies.firstOrNull { it.startsWith("c_user=") }
                        ?.substringAfter("c_user=")?.substringBefore(";")
                    if (userId != null && cookie.isNotBlank()) {
                        saveFbSession(userId, cookie)
                        val pic = "https://graph.facebook.com/$userId/picture?type=normal"
                        withContext(Dispatchers.Main) {
                            Glide.with(this@AutopostFragment)
                                .load(pic)
                                .circleCrop()
                                .into(icon)
                            check.visibility = View.VISIBLE
                        }
                    } else {
                        val location = resp.header("Location") ?: ""
                        val bodyStr = resp.body?.string()
                        val msg = when {
                            location.contains("checkpoint") -> "Akun memerlukan verifikasi (checkpoint)"
                            resp.code in 400..499 -> "Email atau password salah"
                            !bodyStr.isNullOrBlank() -> bodyStr
                            else -> resp.message
                        }
                        withContext(Dispatchers.Main) { showErrorDialog(msg) }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { showErrorDialog("Gagal terhubung ke Facebook") }
            }
        }
    }

    private suspend fun promptTwoFactorCode(): String? = suspendCancellableCoroutine { cont ->
        requireActivity().runOnUiThread {
            val view = layoutInflater.inflate(R.layout.dialog_two_factor, null)
            val codeInput = view.findViewById<EditText>(R.id.edit_code)
            AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Verify") { _, _ ->
                    cont.resume(codeInput.text.toString().trim())
                }
                .setNegativeButton("Batal") { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        }
    }

    private suspend fun promptCheckpointCode(): String? = suspendCancellableCoroutine { cont ->
        requireActivity().runOnUiThread {
            val view = layoutInflater.inflate(R.layout.dialog_checkpoint, null)
            val codeInput = view.findViewById<EditText>(R.id.edit_checkpoint)
            AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Submit") { _, _ ->
                    cont.resume(codeInput.text.toString().trim())
                }
                .setNegativeButton("Batal") { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        }
    }

    private fun saveSession(client: IGClient) {
        try {
            val (clientFile, cookieFile) = sessionFiles()
            client.serialize(clientFile, cookieFile)
        } catch (_: Exception) {
        }
    }

    private suspend fun loadSavedSession(icon: ImageView, check: ImageView) {
        val (clientFile, cookieFile) = sessionFiles()
        if (!clientFile.exists() || !cookieFile.exists()) return
        try {
            val client = IGClient.deserialize(clientFile, cookieFile)
            // simple request to verify session
            client.actions().users().info(client.selfProfile.pk).join()
            igClient = client
            val pic = client.selfProfile.profile_pic_url
            withContext(Dispatchers.Main) {
                Glide.with(this@AutopostFragment)
                    .load(pic)
                    .circleCrop()
                    .into(icon)
                check.visibility = View.VISIBLE
            }
        } catch (_: Exception) {
            clientFile.delete()
            cookieFile.delete()
        }
    }

    private suspend fun loadTwitterSession(icon: ImageView, check: ImageView) {
        val (token, profile, ok) = loadTwitterToken()
        if (!ok || token == null) return
        val twitter = TwitterFactory.getSingleton().apply {
            setOAuthConsumer(BuildConfig.TWITTER_CONSUMER_KEY, BuildConfig.TWITTER_CONSUMER_SECRET)
            oAuthAccessToken = token
        }
        try {
            val user = withContext(Dispatchers.IO) { twitter.verifyCredentials() }
            twitterToken = token
            val pic = profile ?: user.profileImageURLHttps
            withContext(Dispatchers.Main) {
                Glide.with(this@AutopostFragment)
                    .load(pic)
                    .circleCrop()
                    .into(icon)
                check.visibility = View.VISIBLE
            }
        } catch (_: Exception) {
            twitterPrefs().edit().clear().apply()
        }
    }

    private fun launchTwitterLogin() {
        val intent = android.content.Intent(requireContext(), TwitterLoginActivity::class.java)
        twitterLoginLauncher.launch(intent)
    }

    private val twitterLoginLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val token = data.getStringExtra("token") ?: return@registerForActivityResult
            val secret = data.getStringExtra("secret") ?: return@registerForActivityResult
            val profile = data.getStringExtra("profile")
            val access = AccessToken(token, secret)
            twitterToken = access
            saveTwitterToken(access, profile)
            val icon = view?.findViewById<ImageView>(R.id.twitter_icon)
            val check = view?.findViewById<ImageView>(R.id.twitter_check)
            if (icon != null && check != null) {
                Glide.with(this)
                    .load(profile)
                    .circleCrop()
                    .into(icon)
                check.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun runAutopostWorkflow() {
        val logView = requireView().findViewById<android.widget.TextView>(R.id.console_header)
        fun appendLog(msg: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                logView.append("\n$msg")
            }
        }

        val prefs = requireContext().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        val userId = prefs.getString("userId", "") ?: ""
        if (token.isBlank() || userId.isBlank() || igClient == null) {
            appendLog("Autentikasi diperlukan")
            return
        }

        suspend fun fetchClientId(): String? {
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string()
                    try {
                        org.json.JSONObject(body ?: "{}").optJSONObject("data")?.optString("client_id")
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) { null }
        }

        suspend fun fetchPosts(clientId: String): List<InstaPost> {
            val posts = mutableListOf<InstaPost>()
            val client = okhttp3.OkHttpClient()
            val url = "https://papiqo.com/api/insta/posts?client_id=$clientId"
            val req = okhttp3.Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    val body = resp.body?.string()
                    val arr = try { org.json.JSONObject(body ?: "{}").optJSONArray("data") ?: org.json.JSONArray() } catch (_: Exception) { org.json.JSONArray() }
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    val today = java.time.LocalDate.now()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val created = obj.optString("created_at")
                        val createdDate = try {
                            if (created.contains("T")) {
                                java.time.OffsetDateTime.parse(created).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
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
                                    imageUrl = obj.optString("image_url").ifBlank { obj.optString("thumbnail_url") },
                                    createdAt = created,
                                    isVideo = obj.optBoolean("is_video"),
                                    videoUrl = obj.optString("video_url"),
                                    sourceUrl = obj.optString("source_url")
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
            return posts
        }

        suspend fun fetchReported(): Set<String> {
            val set = mutableSetOf<String>()
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("https://papiqo.com/api/link-reports")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return emptySet()
                    val body = resp.body?.string()
                    val arr = try { org.json.JSONObject(body ?: "{}").optJSONArray("data") ?: org.json.JSONArray() } catch (_: Exception) { org.json.JSONArray() }
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("user_id") == userId) set.add(o.optString("shortcode"))
                    }
                }
            } catch (_: Exception) {}
            return set
        }

        fun fileForPost(post: InstaPost): File {
            val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!dir.exists()) dir.mkdirs()
            val name = post.id + if (post.isVideo) ".mp4" else ".jpg"
            return File(dir, name)
        }

        fun coverFileForPost(post: InstaPost): File {
            val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, post.id + ".jpg")
        }

        suspend fun downloadCoverIfNeeded(post: InstaPost): File? {
            val cover = coverFileForPost(post)
            if (cover.exists()) return cover
            val url = post.imageUrl ?: post.sourceUrl
            if (url.isNullOrBlank()) return null
            appendLog("Mengunduh thumbnail…")
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body ?: return null
                    cover.outputStream().use { outStream ->
                        body.byteStream().copyTo(outStream)
                    }
                    cover
                }
            } catch (_: Exception) { null }
        }

        suspend fun downloadIfNeeded(post: InstaPost): File? {
            val out = fileForPost(post)
            if (out.exists()) return out
            val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
            if (url.isNullOrBlank()) return null
            appendLog("Mengunduh konten…")
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body ?: return null
                    out.outputStream().use { outStream ->
                        body.byteStream().copyTo(outStream)
                    }
                    if (post.isVideo) {
                        downloadCoverIfNeeded(post)
                    }
                    out
                }
            } catch (_: Exception) { null }
        }

        suspend fun uploadToInstagram(post: InstaPost, file: File): String? {
            appendLog("Mengunggah konten…")
            return try {
                val result = if (post.isVideo) {
                    val cover = downloadCoverIfNeeded(post) ?: return null
                    igClient!!.actions().timeline().uploadVideo(file, cover, post.caption ?: "").join()
                } else {
                    igClient!!.actions().timeline().uploadPhoto(file, post.caption ?: "").join()
                }
                result.media?.code?.let { "https://instagram.com/p/$it" }
            } catch (_: Exception) { null }
        }

        suspend fun sendLink(shortcode: String, link: String) {
            val json = org.json.JSONObject().apply {
                put("shortcode", shortcode)
                put("user_id", userId)
                put("instagram_link", link)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("https://papiqo.com/api/link-reports")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            try {
                client.newCall(req).execute().use { }
            } catch (_: Exception) {}
        }

        appendLog("Memulai autopost…")
        val clientId = fetchClientId() ?: run { appendLog("Gagal mengambil client id"); return }
        val reported = fetchReported()
        val posts = fetchPosts(clientId)
        for (post in posts) {
            if (reported.contains(post.id)) continue
            appendLog("Memeriksa download…")
            kotlinx.coroutines.delay(3000)
            val file = downloadIfNeeded(post) ?: continue
            kotlinx.coroutines.delay(3000)
            val link = uploadToInstagram(post, file) ?: continue
            kotlinx.coroutines.delay(3000)
            appendLog("Link: $link")
            sendLink(post.id, link)
            kotlinx.coroutines.delay(3000)
        }
        appendLog("Selesai")
    }
}
