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
import android.content.Context
import android.content.Intent
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
    private var premiumActive: Boolean = false

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

    private fun tiktokSessionFile(): File = File(requireContext().filesDir, "tiktok_session.txt")

    private fun youtubePrefs() = requireContext().getSharedPreferences("youtube_auth", Context.MODE_PRIVATE)

    private fun instagramPrefs() = requireContext().getSharedPreferences("instagram_auth", Context.MODE_PRIVATE)

    private fun saveInstagramUsername(username: String) {
        instagramPrefs().edit().putString("username", username).apply()
    }

    private fun loadInstagramUsername(): String? = instagramPrefs().getString("username", null)

    private fun saveYoutubeToken(token: String) {
        youtubePrefs().edit().putString("token", token).apply()
    }

    private fun loadYoutubeToken(): Pair<String?, Boolean> {
        val t = youtubePrefs().getString("token", null)
        return Pair(t, t != null)
    }

    private fun saveFbSession(userId: String, cookie: String) {
        try {
            fbSessionFile().writeText("$userId\n$cookie")
        } catch (_: Exception) {
        }
    }

    private fun saveTikTokSession(cookie: String) {
        try {
            tiktokSessionFile().writeText(cookie)
        } catch (_: Exception) {
        }
    }

    private fun fetchTikTokAvatar(cookie: String): String? {
        val req = okhttp3.Request.Builder()
            .url("https://www.tiktok.com/api/me/")
            .header("Cookie", cookie)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36" +
                    " (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36"
            )
            .build()
        return try {
            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = org.json.JSONObject(body)
                val user = obj.optJSONObject("user")
                user?.optString("avatarLarger") ?: user?.optString("avatarThumb")
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadYoutubeSession(icon: ImageView, check: ImageView) {
        val (token, ok) = loadYoutubeToken()
        if (!ok || token.isNullOrEmpty()) return
        withContext(Dispatchers.Main) { check.visibility = View.VISIBLE }
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

    private suspend fun loadTikTokSession(icon: ImageView, check: ImageView) {
        val file = tiktokSessionFile()
        if (!file.exists()) return
        try {
            val cookie = file.readText()
            val pic = fetchTikTokAvatar(cookie)
            withContext(Dispatchers.Main) {
                if (pic != null) {
                    Glide.with(this@AutopostFragment)
                        .load(pic)
                        .circleCrop()
                        .into(icon)
                }
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
        val tiktokIcon = view.findViewById<ImageView>(R.id.tiktok_icon)
        val tiktokCheck = view.findViewById<ImageView>(R.id.tiktok_check)
        val youtubeIcon = view.findViewById<ImageView>(R.id.youtube_icon)
        val youtubeCheck = view.findViewById<ImageView>(R.id.youtube_check)
        val start = view.findViewById<Button>(R.id.button_start)

        // attempt to load saved session
        lifecycleScope.launch(Dispatchers.IO) {
            loadSavedSession(icon, check)
            loadFbSession(fbIcon, fbCheck)
            loadTwitterSession(twitterIcon, twitterCheck)
            loadTikTokSession(tiktokIcon, tiktokCheck)
            loadYoutubeSession(youtubeIcon, youtubeCheck)
            val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            val token = prefs.getString("token", "") ?: ""
            val userId = prefs.getString("userId", "") ?: ""
            premiumActive = token.isNotBlank() && userId.isNotBlank() &&
                hasActiveSubscription(token, userId)
            withContext(Dispatchers.Main) {
                start.isEnabled = premiumActive
            }
        }

        icon.setOnClickListener { showLoginDialog(icon, check) }
        fbIcon.setOnClickListener { launchFacebookLogin() }
        twitterIcon.setOnClickListener { launchTwitterLogin() }
        tiktokIcon.setOnClickListener { launchTikTokLogin() }
        youtubeIcon.setOnClickListener { launchYoutubeLogin() }
        start.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                if (!premiumActive) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.premium_required), Toast.LENGTH_SHORT).show()
                        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
                        val userId = prefs.getString("userId", "") ?: ""
                        val intent = Intent(requireContext(), PremiumRegistrationActivity::class.java)
                        intent.putExtra("userId", userId)
                        startActivity(intent)
                    }
                } else {
                    runAutopostWorkflow()
                }
            }
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
                saveInstagramUsername(client.selfProfile.username)
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
            saveInstagramUsername(client.selfProfile.username)
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
        val twitter = TwitterFactory().instance.apply {
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

    private fun launchFacebookLogin() {
        val intent = android.content.Intent(requireContext(), FacebookLoginActivity::class.java)
        facebookLoginLauncher.launch(intent)
    }

    private val facebookLoginLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val userId = data.getStringExtra("userId") ?: return@registerForActivityResult
            val cookie = data.getStringExtra("cookie") ?: return@registerForActivityResult
            saveFbSession(userId, cookie)
            val icon = view?.findViewById<ImageView>(R.id.facebook_icon)
            val check = view?.findViewById<ImageView>(R.id.facebook_check)
            val pic = "https://graph.facebook.com/$userId/picture?type=normal"
            if (icon != null && check != null) {
                Glide.with(this)
                    .load(pic)
                    .circleCrop()
                    .into(icon)
                check.visibility = View.VISIBLE
            }
        }
    }

    private fun launchTikTokLogin() {
        val intent = android.content.Intent(requireContext(), TikTokLoginActivity::class.java)
        tiktokLoginLauncher.launch(intent)
    }

    private val tiktokLoginLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val cookie = data.getStringExtra("cookie") ?: return@registerForActivityResult
            saveTikTokSession(cookie)
            val icon = view?.findViewById<ImageView>(R.id.tiktok_icon)
            val check = view?.findViewById<ImageView>(R.id.tiktok_check)
            val pic = fetchTikTokAvatar(cookie)
            if (icon != null && check != null) {
                if (pic != null) {
                    Glide.with(this)
                        .load(pic)
                        .circleCrop()
                        .into(icon)
                }
                check.visibility = View.VISIBLE
            }
        }
    }

    private fun launchYoutubeLogin() {
        val intent = android.content.Intent(requireContext(), YouTubeLoginActivity::class.java)
        youtubeLoginLauncher.launch(intent)
    }

    private val youtubeLoginLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val token = data.getStringExtra("token") ?: return@registerForActivityResult
            saveYoutubeToken(token)
            val icon = view?.findViewById<ImageView>(R.id.youtube_icon)
            val check = view?.findViewById<ImageView>(R.id.youtube_check)
            if (check != null) check.visibility = View.VISIBLE
        }
    }

    private suspend fun hasActiveSubscription(token: String, userId: String): Boolean {
        val client = okhttp3.OkHttpClient()
        val req = okhttp3.Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/premium-subscriptions/user/$userId/active")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string()
                val dataObj = try {
                    org.json.JSONObject(bodyStr ?: "{}").optJSONObject("data")
                } catch (_: Exception) { null }
                resp.isSuccessful && dataObj != null
            }
        } catch (_: Exception) {
            false
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
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
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
            val url = "${BuildConfig.API_BASE_URL}/api/insta/posts?client_id=$clientId"
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
                            val carouselArr = obj.optJSONArray("image_urls")
                                ?: obj.optJSONArray("carousel")
                                ?: obj.optJSONArray("carousel_images")
                            val carousel = mutableListOf<String>()
                            if (carouselArr != null) {
                                for (j in 0 until carouselArr.length()) {
                                    val u = carouselArr.optString(j)
                                    if (u.isNotBlank()) carousel.add(u)
                                }
                            }
                            posts.add(
                                InstaPost(
                                    id = id,
                                    caption = obj.optString("caption"),
                                    imageUrl = obj.optString("image_url").ifBlank { obj.optString("thumbnail_url") }.ifBlank { carousel.firstOrNull() },
                                    createdAt = created,
                                    isVideo = obj.optBoolean("is_video"),
                                    videoUrl = obj.optString("video_url"),
                                    sourceUrl = obj.optString("source_url"),
                                    carouselImages = carousel
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
                .url("${BuildConfig.API_BASE_URL}/api/link-reports")
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

        fun carouselFileForPost(post: InstaPost, index: Int): File {
            val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "${post.id}_${index}.jpg")
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

        suspend fun downloadCarouselImagesIfNeeded(post: InstaPost): List<File> {
            val files = mutableListOf<File>()
            if (post.carouselImages.size <= 1 || post.isVideo) return files
            val client = okhttp3.OkHttpClient()
            for ((idx, url) in post.carouselImages.withIndex()) {
                val f = carouselFileForPost(post, idx)
                if (f.exists()) { files.add(f); continue }
                if (url.isBlank()) continue
                appendLog("Mengunduh gambar ${idx + 1}/${post.carouselImages.size}…")
                val req = okhttp3.Request.Builder().url(url).build()
                try {
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body ?: return@use
                        f.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                    }
                } catch (_: Exception) {}
                if (f.exists()) files.add(f)
            }
            return files
        }

        fun shareCarousel(post: InstaPost) {
            val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            val files: List<File> = if (!post.isVideo && post.carouselImages.size > 1) {
                post.carouselImages.indices.map { carouselFileForPost(post, it) }
            } else {
                listOf(fileForPost(post))
            }

            val intent = if (files.size > 1) Intent(Intent.ACTION_SEND_MULTIPLE) else Intent(Intent.ACTION_SEND)
            intent.type = if (post.isVideo) "video/*" else "image/*"
            val uris = ArrayList<android.net.Uri>()
            files.filter { it.exists() }.forEach { f ->
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().packageName + ".fileprovider",
                    f
                )
                uris.add(uri)
            }
            if (uris.isNotEmpty()) {
                if (uris.size == 1) {
                    intent.putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
                if (!url.isNullOrBlank()) intent.putExtra(Intent.EXTRA_TEXT, url)
            }
            if (!post.caption.isNullOrBlank()) {
                intent.putExtra(Intent.EXTRA_TEXT, post.caption)
            }
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("caption", post.caption ?: ""))
            startActivity(Intent.createChooser(intent, "Share via"))
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
                .url("${BuildConfig.API_BASE_URL}/api/link-reports")
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
            if (!post.isVideo && post.carouselImages.size > 1) {
                downloadCarouselImagesIfNeeded(post)
            }
            kotlinx.coroutines.delay(3000)
            val link = uploadToInstagram(post, file) ?: continue
            kotlinx.coroutines.delay(3000)
            appendLog("Link: $link")
            sendLink(post.id, link)
            withContext(Dispatchers.Main) { shareCarousel(post) }
            kotlinx.coroutines.delay(3000)
        }
        appendLog("Selesai")
    }
}
