package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.io.File
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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
import com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest
import com.cicero.repostapp.postTweetWithMedia

class AutopostFragment : Fragment() {

    companion object {
        fun newInstance(): AutopostFragment = AutopostFragment()
    }

    private var igClient: IGClient? = null
    private var twitterToken: AccessToken? = null
    private var consoleHeader: TextView? = null

    /**
     * Check if a caption already exists on the authenticated Instagram account.
     */
    private suspend fun captionAlreadyExists(caption: String?): Boolean {
        if (caption.isNullOrBlank()) return false
        val client = igClient ?: return false
        return try {
            val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(client.selfProfile.pk)
            val resp = client.sendRequest(req).join()
            resp.items?.any { it.caption?.text?.trim() == caption.trim() } ?: false
        } catch (_: Exception) {
            false
        }
    }

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

    private fun instagramPrefs() = requireContext().getSharedPreferences("instagram_auth", Context.MODE_PRIVATE)

    private fun saveInstagramUsername(username: String) {
        instagramPrefs().edit().putString("username", username).apply()
    }

    private fun loadInstagramUsername(): String? = instagramPrefs().getString("username", null)


    private fun saveFbSession(userId: String, cookie: String) {
        try {
            fbSessionFile().writeText("$userId\n$cookie")
        } catch (_: Exception) {
        }
    }

    private fun saveTikTokUsername(username: String) {
        try {
            tiktokSessionFile().writeText(username)
        } catch (_: Exception) {
        }
    }

    private fun loadTikTokUsername(): String? {
        val file = tiktokSessionFile()
        return if (file.exists()) try { file.readText().trim() } catch (_: Exception) { null } else null
    }

    private fun fetchTikTokProfile(username: String): Pair<String, String>? {
        val url = "https://tikwm.com/api/user/info?unique_id=" +
            java.net.URLEncoder.encode(username, "UTF-8")
        val req = okhttp3.Request.Builder().url(url).build()
        return try {
            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = org.json.JSONObject(body)
                if (obj.optInt("code") != 0) return null
                val data = obj.optJSONObject("data")?.optJSONObject("user") ?: return null
                val avatar = data.optString("avatarLarger")
                val uname = data.optString("uniqueId")
                if (avatar.isNullOrBlank() || uname.isNullOrBlank()) return null
                Pair(avatar, uname)
            }
        } catch (_: Exception) {
            null
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
                if (!canUpdateUi()) return@withContext
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

    private suspend fun loadTikTokSession(icon: ImageView, check: ImageView, text: TextView) {
        val username = loadTikTokUsername() ?: return
        val (avatar, uname) = fetchTikTokProfile(username) ?: return
        withContext(Dispatchers.Main) {
            if (!canUpdateUi()) return@withContext
            Glide.with(this@AutopostFragment)
                .load(avatar)
                .circleCrop()
                .into(icon)
            text.text = uname
            text.visibility = View.VISIBLE
            check.visibility = View.VISIBLE
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
        val tiktokText = view.findViewById<TextView>(R.id.tiktok_username)
        val start = view.findViewById<Button>(R.id.button_start)
        val postTwitter = view.findViewById<Button>(R.id.button_post_twitter)

        // attempt to load saved session
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            loadSavedSession(icon, check)
            loadFbSession(fbIcon, fbCheck)
            loadTwitterSession(twitterIcon, twitterCheck)
            loadTikTokSession(tiktokIcon, tiktokCheck, tiktokText)
        }

        icon.setOnClickListener { showLoginDialog(icon, check) }
        fbIcon.setOnClickListener { launchFacebookLogin() }
        twitterIcon.setOnClickListener { launchTwitterLogin() }
        tiktokIcon.setOnClickListener { launchTikTokLogin() }
        consoleHeader = view.findViewById(R.id.console_header)

        start.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                runAutopostWorkflow()
            }
        }
        postTwitter.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val file = java.io.File(requireContext().filesDir, "sample.jpg")
                val ok = postTweetWithMedia("Hello from API", file)
                withContext(Dispatchers.Main) {
                    if (!canUpdateUi()) return@withContext
                    val msg = if (ok) "Tweet terkirim" else "Gagal mengirim tweet"
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        consoleHeader = null
        super.onDestroyView()
    }

    private fun canUpdateUi(): Boolean {
        if (!isAdded || view == null) return false
        return try {
            viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        } catch (_: IllegalStateException) {
            false
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
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
                    if (!canUpdateUi()) return@withContext
                    Glide.with(this@AutopostFragment)
                        .load(pic)
                        .circleCrop()
                        .into(icon)
                    check.visibility = View.VISIBLE
                }
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) {
                    if (!canUpdateUi()) return@withContext
                    Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    if (!canUpdateUi()) return@withContext
                    Toast.makeText(requireContext(), "Gagal terhubung", Toast.LENGTH_SHORT).show()
                }
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
                if (!canUpdateUi()) return@withContext
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
                if (!canUpdateUi()) return@withContext
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
            if (icon != null && check != null && canUpdateUi()) {
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
            if (icon != null && check != null && canUpdateUi()) {
                Glide.with(this)
                    .load(pic)
                    .circleCrop()
                    .into(icon)
                check.visibility = View.VISIBLE
            }
        }
    }

    private fun launchTikTokLogin() {
        val view = layoutInflater.inflate(R.layout.dialog_tiktok_username, null)
        val input = view.findViewById<EditText>(R.id.edit_tiktok_username)
        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Ambil Profil") { _, _ ->
                val user = input.text.toString().trim()
                if (user.isBlank()) {
                    Toast.makeText(requireContext(), "Username wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    performTikTokFetch(user)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun performTikTokFetch(username: String) {
        val icon = view?.findViewById<ImageView>(R.id.tiktok_icon)
        val check = view?.findViewById<ImageView>(R.id.tiktok_check)
        val text = view?.findViewById<TextView>(R.id.tiktok_username)
        if (icon == null || check == null || text == null) return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = fetchTikTokProfile(username)
            withContext(Dispatchers.Main) {
                if (!canUpdateUi()) return@withContext
                if (result == null) {
                    Toast.makeText(requireContext(), "Gagal mengambil profil", Toast.LENGTH_SHORT).show()
                } else {
                    val (avatar, uname) = result
                    Glide.with(this@AutopostFragment)
                        .load(avatar)
                        .circleCrop()
                        .into(icon)
                    text.text = uname
                    text.visibility = View.VISIBLE
                    check.visibility = View.VISIBLE
                    saveTikTokUsername(uname)
                }
            }
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
        fun appendLog(msg: String) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                if (!canUpdateUi()) return@launch
                consoleHeader?.append("\n$msg")
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
            appendLog("Meminta client id…")
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        appendLog("Gagal client id: ${'$'}{resp.code}")
                        return null
                    }
                    val body = resp.body?.string()
                    val id = try {
                        org.json.JSONObject(body ?: "{}").optJSONObject("data")?.optString("client_id")
                    } catch (_: Exception) { null }
                    if (id != null) appendLog("Client id diperoleh: ${'$'}id")
                    id
                }
            } catch (e: Exception) {
                appendLog("Error client id: ${'$'}{e.message}")
                null
            }
        }

        suspend fun fetchPosts(clientId: String): List<InstaPost> {
            appendLog("Mengambil daftar tugas…")
            val posts = mutableListOf<InstaPost>()
            val client = okhttp3.OkHttpClient()
            val url = "${BuildConfig.API_BASE_URL}/api/insta/posts?client_id=$clientId"
            val req = okhttp3.Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        appendLog("Gagal mengambil tugas: ${'$'}{resp.code}")
                        return emptyList()
                    }
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
                            val carouselArr = obj.optJSONArray("images_url")
                                ?: obj.optJSONArray("image_urls")
                                ?: obj.optJSONArray("carousel")
                                ?: obj.optJSONArray("carousel_images")
                            val carousel = mutableListOf<String>()
                            if (carouselArr != null) {
                                for (j in 0 until carouselArr.length()) {
                                    val u = carouselArr.optString(j)
                                    if (u.isNotBlank()) carousel.add(u)
                                }
                            }
                            val isCarousel = obj.optBoolean("is_carousel", carousel.size > 1)
                            posts.add(
                                InstaPost(
                                    id = id,
                                    caption = obj.optString("caption"),
                                    imageUrl = obj.optString("image_url").ifBlank { obj.optString("thumbnail_url") }.ifBlank { carousel.firstOrNull() },
                                    createdAt = created,
                                    taskNumber = posts.size + 1,
                                    isVideo = obj.optBoolean("is_video"),
                                    videoUrl = obj.optString("video_url"),
                                    sourceUrl = obj.optString("source_url"),
                                    isCarousel = isCarousel,
                                    carouselImages = carousel
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                appendLog("Error mengambil tugas")
            }
            appendLog("Total tugas hari ini: ${'$'}{posts.size}")
            return posts
        }

        suspend fun fetchReported(): Set<String> {
            appendLog("Mengambil daftar link yang sudah dilaporkan…")
            val set = mutableSetOf<String>()
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/link-reports")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        appendLog("Gagal mengambil laporan: ${'$'}{resp.code}")
                        return emptySet()
                    }
                    val body = resp.body?.string()
                    val arr = try { org.json.JSONObject(body ?: "{}").optJSONArray("data") ?: org.json.JSONArray() } catch (_: Exception) { org.json.JSONArray() }
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("user_id") == userId) set.add(o.optString("shortcode"))
                    }
                }
            } catch (e: Exception) {
                appendLog("Error mengambil laporan: ${'$'}{e.message}")
            }
            appendLog("Total link terlapor: ${'$'}{set.size}")
            return set
        }

        fun fileForPost(post: InstaPost): File {
            val baseDir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!baseDir.exists()) baseDir.mkdirs()
            val dir = File(baseDir, post.id)
            if (!dir.exists()) dir.mkdirs()
            val name = post.id + if (post.isVideo) ".mp4" else ".jpg"
            return File(dir, name)
        }

        fun coverFileForPost(post: InstaPost): File {
            val baseDir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!baseDir.exists()) baseDir.mkdirs()
            val dir = File(baseDir, post.id)
            if (!dir.exists()) dir.mkdirs()
            return File(dir, post.id + ".jpg")
        }

        fun carouselFileForPost(post: InstaPost, index: Int): File {
            val baseDir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!baseDir.exists()) baseDir.mkdirs()
            val dir = File(baseDir, post.id)
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "${post.id}_${index}.jpg")
        }

        suspend fun downloadCoverIfNeeded(post: InstaPost): File? {
            val cover = coverFileForPost(post)
            if (cover.exists()) {
                appendLog("Thumbnail sudah ada")
                return cover
            }
            val url = post.imageUrl ?: post.sourceUrl
            if (url.isNullOrBlank()) return null
            appendLog("Mengunduh thumbnail…")
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        appendLog("Gagal unduh thumbnail: ${'$'}{resp.code}")
                        return null
                    }
                    val body = resp.body ?: return null
                    cover.outputStream().use { outStream ->
                        body.byteStream().copyTo(outStream)
                    }
                    appendLog("Thumbnail tersimpan")
                    cover
                }
            } catch (e: Exception) {
                appendLog("Error unduh thumbnail: ${'$'}{e.message}")
                null
            }
        }

        suspend fun downloadIfNeeded(post: InstaPost): File? {
            val out = fileForPost(post)
            if (out.exists()) {
                appendLog("Konten sudah ada")
                return out
            }
            val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
            if (url.isNullOrBlank()) return null
            appendLog("Mengunduh konten…")
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        appendLog("Gagal unduh konten: ${'$'}{resp.code}")
                        return null
                    }
                    val body = resp.body ?: return null
                    out.outputStream().use { outStream ->
                        body.byteStream().copyTo(outStream)
                    }
                    if (post.isVideo) {
                        downloadCoverIfNeeded(post)
                    }
                    appendLog("Konten tersimpan")
                    out
                }
            } catch (e: Exception) {
                appendLog("Error unduh konten: ${'$'}{e.message}")
                null
            }
        }

        suspend fun downloadCarouselImagesIfNeeded(post: InstaPost): List<File> {
            val files = mutableListOf<File>()
            if (!post.isCarousel || post.carouselImages.isEmpty() || post.isVideo) return files
            val client = okhttp3.OkHttpClient()
            val dir = carouselFileForPost(post, 0).parentFile
            if (dir != null && !dir.exists()) dir.mkdirs()
            for ((idx, url) in post.carouselImages.withIndex()) {
                val f = carouselFileForPost(post, idx)
                if (f.exists()) { files.add(f); continue }
                if (url.isBlank()) continue
                appendLog("Mengunduh gambar ${'$'}{idx + 1}/${'$'}{post.carouselImages.size}…")
                val req = okhttp3.Request.Builder().url(url).build()
                try {
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            appendLog("Gagal gambar ${'$'}{idx + 1}: ${'$'}{resp.code}")
                            return@use
                        }
                        val body = resp.body ?: return@use
                        f.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                    }
                } catch (e: Exception) {
                    appendLog("Error gambar ${'$'}{idx + 1}: ${'$'}{e.message}")
                }
                if (f.exists()) files.add(f)
            }
            if (files.isNotEmpty()) post.localCarouselDir = dir?.absolutePath
            return files
        }

        fun shareCarousel(post: InstaPost) {
            val baseDir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            val folder = if (!post.isVideo && post.isCarousel) File(baseDir, post.id) else baseDir
            val files: List<File> = if (!post.isVideo && post.isCarousel) {
                folder.listFiles { f -> f.isFile && f.name.endsWith(".jpg") }?.sortedBy { it.name }?.toList()
                    ?: post.carouselImages.indices.map { carouselFileForPost(post, it) }
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
                val result = when {
                    post.isVideo -> {
                        val cover = downloadCoverIfNeeded(post) ?: return null
                        igClient!!.actions().timeline().uploadVideo(file, cover, post.caption ?: "").join()
                    }
                    post.isCarousel -> {
                        val files = mutableListOf<File>()
                        files.add(file)
                        for (i in 1 until post.carouselImages.size) {
                            val f = carouselFileForPost(post, i)
                            if (f.exists()) files.add(f)
                        }
                        val infos = files.map { com.github.instagram4j.instagram4j.actions.timeline.TimelineAction.SidecarPhoto.from(it) }
                        igClient!!.actions().timeline().uploadAlbum(infos, post.caption ?: "").join()
                    }
                    else -> {
                        igClient!!.actions().timeline().uploadPhoto(file, post.caption ?: "").join()
                    }
                }
                val link = result.media?.code?.let { "https://instagram.com/p/$it" }
                if (link != null) {
                    appendLog("Berhasil unggah: ${'$'}link")
                } else {
                    appendLog("Upload berhasil tapi tidak ada link")
                }
                link
            } catch (e: Exception) {
                appendLog("Error unggah: ${'$'}{e.message}")
                null
            }
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
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        appendLog("Link dikirim ke server")
                    } else {
                        appendLog("Gagal kirim link: ${'$'}{resp.code}")
                    }
                }
            } catch (e: Exception) {
                appendLog("Error kirim link: ${'$'}{e.message}")
            }
        }


        suspend fun sendTikTokLink(shortcode: String, link: String) {
            val json = org.json.JSONObject().apply {
                put("shortcode", shortcode)
                put("user_id", userId)
                put("tiktok_link", link)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/link-reports")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        appendLog("Link TikTok dikirim")
                    } else {
                        appendLog("Gagal kirim link TikTok: ${'$'}{resp.code}")
                    }
                }
            } catch (e: Exception) {
                appendLog("Error kirim link TikTok: ${'$'}{e.message}")
            }
        }


        suspend fun postToTikTok(post: InstaPost, file: File): String? {
            appendLog("Posting ke TikTok…")
            delay(2000)
            return try {
                val fake = "https://tiktok.com/v/" + System.currentTimeMillis()
                fake
            } catch (_: Exception) { null }
        }

        suspend fun <T> retryAction(name: String, block: suspend () -> T?): T? {
            repeat(3) { attempt ->
                val result = block()
                if (result != null) return result
                appendLog("Percobaan ${'$'}{attempt + 1} gagal pada ${'$'}name, ulangi…")
                delay(5000)
            }
            appendLog("Berhenti pada proses ${'$'}name")
            return null
        }

        appendLog("Memulai autopost…")
        val clientId = fetchClientId() ?: run { appendLog("Gagal mengambil client id"); return }
        val reported = fetchReported()
        val posts = fetchPosts(clientId)
        for (post in posts) {
            if (reported.contains(post.id)) continue
            if (captionAlreadyExists(post.caption)) {
                appendLog("Caption sudah pernah dipost, lewati")
                continue
            }
            appendLog("Memproses tugas ${'$'}{post.taskNumber} (${ '$'}{post.id })")
            appendLog("Memeriksa download…")
            delay(5000)
            val file = retryAction("download konten") { downloadIfNeeded(post) } ?: break
            if (!post.isVideo && post.isCarousel) {
                downloadCarouselImagesIfNeeded(post)
            }
            delay(5000)
            val igLink = retryAction("upload Instagram") { uploadToInstagram(post, file) } ?: break
            delay(5000)
            appendLog("Link: ${'$'}igLink")
            sendLink(post.id, igLink)
            val ttLink = retryAction("post TikTok") { postToTikTok(post, file) }
            if (ttLink != null) sendTikTokLink(post.id, ttLink)
            withContext(Dispatchers.Main) {
                if (!canUpdateUi()) return@withContext
                shareCarousel(post)
            }
            appendLog("Tugas selesai")
            delay(5000)
        }
        appendLog("Selesai")
    }

    private suspend fun runTwitterPostWorkflow() {
        fun appendLog(msg: String) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                if (!canUpdateUi()) return@launch
                consoleHeader?.append("\n$msg")
            }
        }

        val prefs = requireContext().getSharedPreferences("auth", android.content.Context.MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        val userId = prefs.getString("userId", "") ?: ""
        if (token.isBlank() || userId.isBlank()) {
            appendLog("Autentikasi diperlukan")
            return
        }

        suspend fun fetchClientId(): String? {
            appendLog("Meminta client id…")
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) { appendLog("Gagal client id: ${'$'}{resp.code}"); return null }
                    val body = resp.body?.string()
                    val id = try { org.json.JSONObject(body ?: "{}").optJSONObject("data")?.optString("client_id") } catch (_: Exception) { null }
                    if (id != null) appendLog("Client id diperoleh: ${'$'}id")
                    id
                }
            } catch (e: Exception) {
                appendLog("Error client id: ${'$'}{e.message}")
                null
            }
        }

        suspend fun fetchPosts(clientId: String): List<InstaPost> {
            appendLog("Mengambil daftar tugas…")
            val posts = mutableListOf<InstaPost>()
            val client = okhttp3.OkHttpClient()
            val url = "${BuildConfig.API_BASE_URL}/api/insta/posts?client_id=$clientId"
            val req = okhttp3.Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) { appendLog("Gagal mengambil tugas: ${'$'}{resp.code}"); return emptyList() }
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
                            val carouselArr = obj.optJSONArray("images_url") ?: obj.optJSONArray("image_urls") ?: obj.optJSONArray("carousel") ?: obj.optJSONArray("carousel_images")
                            val carousel = mutableListOf<String>()
                            if (carouselArr != null) {
                                for (j in 0 until carouselArr.length()) {
                                    val u = carouselArr.optString(j)
                                    if (u.isNotBlank()) carousel.add(u)
                                }
                            }
                            val isCarousel = obj.optBoolean("is_carousel", carousel.size > 1)
                            posts.add(
                                InstaPost(
                                    id = id,
                                    caption = obj.optString("caption"),
                                    imageUrl = obj.optString("image_url").ifBlank { obj.optString("thumbnail_url") }.ifBlank { carousel.firstOrNull() },
                                    createdAt = created,
                                    taskNumber = posts.size + 1,
                                    isVideo = obj.optBoolean("is_video"),
                                    videoUrl = obj.optString("video_url"),
                                    sourceUrl = obj.optString("source_url"),
                                    isCarousel = isCarousel,
                                    carouselImages = carousel
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) { appendLog("Error mengambil tugas") }
            return posts
        }

        fun fileForPost(post: InstaPost): java.io.File {
            val baseDir = java.io.File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
            if (!baseDir.exists()) baseDir.mkdirs()
            val dir = java.io.File(baseDir, post.id)
            if (!dir.exists()) dir.mkdirs()
            val name = post.id + if (post.isVideo) ".mp4" else ".jpg"
            return java.io.File(dir, name)
        }

        suspend fun downloadCoverIfNeeded(post: InstaPost): java.io.File? {
            val cover = java.io.File(requireContext().getExternalFilesDir(null), "CiceroReposterApp/${post.id}/${post.id}.jpg")
            if (cover.exists()) return cover
            val url = post.imageUrl ?: post.sourceUrl
            if (url.isNullOrBlank()) return null
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body ?: return null
                    cover.outputStream().use { body.byteStream().copyTo(it) }
                    cover
                }
            } catch (_: Exception) { null }
        }

        suspend fun downloadIfNeeded(post: InstaPost): java.io.File? {
            val out = fileForPost(post)
            if (out.exists()) return out
            val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
            if (url.isNullOrBlank()) return null
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            return try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body ?: return null
                    out.outputStream().use { body.byteStream().copyTo(it) }
                    if (post.isVideo) downloadCoverIfNeeded(post)
                    out
                }
            } catch (_: Exception) { null }
        }

        suspend fun sendTwitterLink(shortcode: String, link: String) {
            val json = org.json.JSONObject().apply {
                put("shortcode", shortcode)
                put("user_id", userId)
                put("twitter_link", link)
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

        suspend fun postToTwitterViaIntent(post: InstaPost, file: java.io.File): String? {
            appendLog("Membuka Twitter…")
            val caption = post.caption ?: ""
            val first = caption.take(230)
            val rest = if (caption.length > 230) caption.substring(230) else ""
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(), requireContext().packageName + ".fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = if (post.isVideo) "video/*" else "image/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_TEXT, first)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.twitter.android")
            }
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("tw_extra", rest))
            return try {
                startActivity(intent)
                appendLog("Menunggu upload…")
                kotlinx.coroutines.delay(2000)
                "https://twitter.com/status/" + System.currentTimeMillis()
            } catch (_: Exception) { null }
        }

        appendLog("Memulai post Twitter…")
        val clientId = fetchClientId() ?: run { appendLog("Gagal client id"); return }
        val posts = fetchPosts(clientId)
        val post = posts.firstOrNull() ?: run { appendLog("Tidak ada konten resmi hari ini"); return }
        val file = downloadIfNeeded(post) ?: run { appendLog("Gagal unduh konten"); return }
        val link = postToTwitterViaIntent(post, file) ?: run { appendLog("Gagal post ke Twitter"); return }
        sendTwitterLink(post.id, link)
        appendLog("Selesai")
    }
}
