package com.cicero.repostapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.IGClient.Builder.LoginHandler
import com.github.instagram4j.instagram4j.actions.timeline.TimelineAction
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import com.github.instagram4j.instagram4j.models.media.timeline.ImageCarouselItem
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineCarouselMedia
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineImageMedia
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineVideoMedia
import com.github.instagram4j.instagram4j.models.media.timeline.VideoCarouselItem
import com.github.instagram4j.instagram4j.models.user.User
import com.github.instagram4j.instagram4j.requests.accounts.AccountsLogoutRequest
import com.github.instagram4j.instagram4j.requests.media.MediaActionRequest
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.auth.RequestToken
import twitter4j.conf.ConfigurationBuilder
import com.cicero.repostapp.BuildConfig
import com.cicero.repostapp.TwitterAuthManager
import com.cicero.repostapp.TikwmApi
import com.cicero.repostapp.TiktokSessionManager
import java.io.File
import java.util.concurrent.Callable

data class PostInfo(
    val code: String,
    val id: String,
    val caption: String?,
    val isVideo: Boolean,
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val coverUrl: String? = null
)

@Suppress("DEPRECATION")
class InstaLoginFragment : Fragment(R.layout.fragment_insta_login) {
    private lateinit var loginContainer: View
    private lateinit var profileContainer: View
    private lateinit var startButton: Button
    private lateinit var likeCheckbox: android.widget.CheckBox
    private lateinit var repostCheckbox: android.widget.CheckBox
    private lateinit var commentCheckbox: android.widget.CheckBox
    private lateinit var badgeView: ImageView
    private lateinit var logContainer: android.widget.LinearLayout
    private lateinit var logScroll: android.widget.ScrollView
    private lateinit var avatarView: ImageView
    private lateinit var usernameView: TextView
    private lateinit var nameView: TextView
    private lateinit var bioView: TextView
    private lateinit var postsView: TextView
    private lateinit var followersView: TextView
    private lateinit var followingView: TextView
    private lateinit var twitterImage: ImageView
    private lateinit var twitterUsernameView: TextView
    private lateinit var tiktokImage: ImageView
    private lateinit var tiktokUsernameView: TextView
    private var twitter: Twitter? = null
    private var twitterRequestToken: RequestToken? = null
    private val clientFile: File by lazy { File(requireContext().filesDir, "igclient.ser") }
    private val cookieFile: File by lazy { File(requireContext().filesDir, "igcookie.ser") }
    private var currentUsername: String? = null
    private var token: String = ""
    private var userId: String = ""
    private var targetUsername: String = "polres_ponorogo"
    private var isPremium: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val username = view.findViewById<EditText>(R.id.input_username)
        val password = view.findViewById<EditText>(R.id.input_password)
        loginContainer = view.findViewById(R.id.login_container)
        profileContainer = view.findViewById(R.id.profile_layout)
        val profileView = view.findViewById<View>(R.id.profile_container)
        avatarView = profileView.findViewById(R.id.image_avatar)
        usernameView = profileView.findViewById(R.id.text_username)
        nameView = profileView.findViewById(R.id.text_name)
        bioView = profileView.findViewById(R.id.text_bio)
        postsView = profileView.findViewById(R.id.stat_posts)
        followersView = profileView.findViewById(R.id.stat_followers)
        followingView = profileView.findViewById(R.id.stat_following)
        profileView.findViewById<View>(R.id.text_nrp).visibility = View.GONE
        profileView.findViewById<View>(R.id.info_container).visibility = View.GONE
        profileView.findViewById<Button>(R.id.button_logout).visibility = View.GONE

        val twitterContainer = view.findViewById<View>(R.id.twitter_container)
        twitterImage = twitterContainer.findViewById(R.id.image_twitter)
        twitterUsernameView = twitterContainer.findViewById(R.id.text_twitter_username)

        val tiktokContainer = view.findViewById<View>(R.id.tiktok_container)
        tiktokImage = tiktokContainer.findViewById(R.id.image_tiktok)
        tiktokUsernameView = tiktokContainer.findViewById(R.id.text_tiktok_username)
        tiktokImage.setOnClickListener { showTiktokDialog() }

        startButton = view.findViewById(R.id.button_start)
        likeCheckbox = view.findViewById(R.id.checkbox_like)
        repostCheckbox = view.findViewById(R.id.checkbox_repost)
        commentCheckbox = view.findViewById(R.id.checkbox_comment)
        badgeView = profileView.findViewById(R.id.image_badge)
        logContainer = view.findViewById(R.id.log_container)
        logScroll = view.findViewById(R.id.log_scroll)

        val authPrefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        token = authPrefs.getString("token", "") ?: ""
        userId = authPrefs.getString("userId", "") ?: ""
        fetchTargetAccount()

        startButton.setOnClickListener {
            if (!isPremium) {
                Toast.makeText(requireContext(), "Fitur ini hanya untuk pengguna premium", Toast.LENGTH_SHORT).show()
            } else {
                val doLike = likeCheckbox.isChecked
                val doRepost = repostCheckbox.isChecked
                val doComment = commentCheckbox.isChecked
                if (doLike || doRepost || doComment) {
                    fetchTodayPosts(doLike, doRepost, doComment)
                } else {
                    Toast.makeText(requireContext(), "Pilih setidaknya satu aksi", Toast.LENGTH_SHORT).show()
                }
            }
        }

        checkSubscriptionStatus(currentUsername)

        restoreSession()
        updateTwitterStatus()
        updateTiktokStatus()

        twitterImage.setOnClickListener { startTwitterLogin() }

        view.findViewById<Button>(R.id.button_login_insta).setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (user.isNotBlank() && pass.isNotBlank()) {
                performLogin(user, pass)
            } else {
                Toast.makeText(requireContext(), "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateTwitterStatus()
        updateTiktokStatus()
    }

    private fun performLogin(user: String, pass: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val codePrompt = Callable {
                runBlocking { promptCode() }
            }

            val twoFactorHandler = LoginHandler { client, resp ->
                IGChallengeUtils.resolveTwoFactor(client, resp, codePrompt)
            }
            val challengeHandler = LoginHandler { client, resp ->
                IGChallengeUtils.resolveChallenge(client, resp, codePrompt)
            }

            try {
                val client = IGClient.builder()
                    .username(user)
                    .password(pass)
                    .onTwoFactor(twoFactorHandler)
                    .onChallenge(challengeHandler)
                    .login()
                client.serialize(clientFile, cookieFile)
                val info = client.actions().users().info(client.selfProfile.pk).join()
                withContext(Dispatchers.Main) {
                    displayProfile(info)
                }
                ensureRemoteData(info)
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal login: ${e.loginResponse.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("InstaLoginFragment", "Login failed", e)
                withContext(Dispatchers.Main) {
                    val message = e.message ?: e.toString()
                    Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun promptCode(): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_two_factor, null)
            val input = view.findViewById<EditText>(R.id.edit_code)
            AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    cont.resume(input.text.toString()) {}
                }
                .setNegativeButton("Batal") { _, _ ->
                    cont.resume("") {}
                }
                .show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayProfile(info: User?) {
        usernameView.text = "@${info?.username ?: ""}"
        nameView.text = info?.full_name ?: ""
        postsView.text = info?.media_count?.toString() ?: "0"
        followersView.text = info?.follower_count?.toString() ?: "0"
        followingView.text = info?.following_count?.toString() ?: "0"
        val url = info?.profile_pic_url
        if (!url.isNullOrBlank()) {
            Glide.with(this)
                .load(url)
                .circleCrop()
                .into(avatarView)
        } else {
            avatarView.setImageDrawable(null)
        }
        bioView.text = info?.biography ?: ""
        loginContainer.visibility = View.GONE
        profileContainer.visibility = View.VISIBLE
        currentUsername = info?.username
        currentUsername?.let { loadSavedLogs(it) }

        ensureRemoteData(info)
        checkSubscriptionStatus(info?.username)
    }

    private fun restoreSession() {
        CoroutineScope(Dispatchers.IO).launch {
            if (clientFile.exists() && cookieFile.exists()) {
                try {
                val client = IGClient.deserialize(clientFile, cookieFile)
                val info = client.actions().users().info(client.selfProfile.pk).join()
                withContext(Dispatchers.Main) { displayProfile(info) }
                ensureRemoteData(info)
                checkSubscriptionStatus(info?.username)
                } catch (_: Exception) {
                    // ignore invalid session
                }
            }
        }
    }

    private fun startTwitterLogin() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = ConfigurationBuilder()
                    .setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY)
                    .setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET)
                    .setUseSSL(true)
                    .build()
                twitter = TwitterFactory(config).instance
                val reqToken = twitter?.getOAuthRequestToken("repostapp://twitter-callback")
                if (reqToken != null) {
                    TwitterAuthManager.saveRequestToken(requireContext(), reqToken)
                }
                twitterRequestToken = reqToken
                val url = reqToken?.authorizationURL ?: return@launch
                withContext(Dispatchers.Main) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menghubungi Twitter", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateTwitterStatus() {
        val stored = TwitterAuthManager.loadAccessToken(requireContext())
        if (stored != null) {
            val (accessToken, accessSecret) = stored
            val config = ConfigurationBuilder()
                .setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY)
                .setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET)
                .setOAuthAccessToken(accessToken)
                .setOAuthAccessTokenSecret(accessSecret)
                .build()
            twitter = TwitterFactory(config).instance
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val tw = twitter ?: return@launch
                try {
                    val user = tw.verifyCredentials()
                    withContext(Dispatchers.Main) {
                        twitterUsernameView.text = "@${'$'}{user.screenName}"
                        twitterUsernameView.visibility = View.VISIBLE
                        Glide.with(this@InstaLoginFragment)
                            .load(user.profileImageURLHttps)
                            .circleCrop()
                            .into(twitterImage)
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        twitterImage.setImageResource(R.drawable.twitter_icon)
                        twitterUsernameView.visibility = View.GONE
                    }
                }
            }
        } else {
            twitterImage.setImageResource(R.drawable.twitter_icon)
            twitterUsernameView.visibility = View.GONE
        }
    }

    private fun updateTiktokStatus() {
        val profile = TiktokSessionManager.loadProfile(requireContext())
        if (profile != null) {
            val username = profile.optString("uniqueId")
            tiktokUsernameView.text = "@$username"
            tiktokUsernameView.visibility = View.VISIBLE
            val avatarUrl = profile.optString("avatarLarger", profile.optString("avatarThumb"))
            if (avatarUrl.isNotBlank()) {
                Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .into(tiktokImage)
            } else {
                tiktokImage.setImageResource(R.drawable.tiktok_icon)
            }
        } else {
            tiktokImage.setImageResource(R.drawable.tiktok_icon)
            tiktokUsernameView.visibility = View.GONE
        }
    }

    private fun checkSubscriptionStatus(username: String?) {
        val user = username ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
                val req = Request.Builder()
                    .url("https://papiqo.com/api/premium-subscriptions/user/$user/active")
                    .header("Authorization", "Bearer $token")
                    .build()
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string()
                    val dataObj = try {
                        JSONObject(body ?: "{}").optJSONObject("data")
                    } catch (_: Exception) { null }
                    isPremium = resp.isSuccessful && dataObj != null
                    withContext(Dispatchers.Main) {
                        badgeView.setImageResource(
                            if (isPremium) R.drawable.ic_badge_premium else R.drawable.ic_badge_basic
                        )
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    badgeView.setImageResource(R.drawable.ic_badge_basic)
                }
            }
        }
    }

    private fun ensureRemoteData(info: User?) {
        val username = info?.username ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            try {
                val checkUserReq = Request.Builder()
                    .url("https://papiqo.com/api/insta/instagram-user?username=$username")
                    .header("Authorization", "Bearer $token")
                    .build()
                val userExists = client.newCall(checkUserReq).execute().use { it.isSuccessful }
                if (!userExists) {
                    val fetchReq = Request.Builder()
                        .url("https://papiqo.com/api/insta/rapid-profile?username=$username")
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(fetchReq).execute().close()
                }

                val checkSubReq = Request.Builder()
                    .url("https://papiqo.com/api/premium-subscriptions/user/$username/active")
                    .header("Authorization", "Bearer $token")
                    .build()
                val subExists = client.newCall(checkSubReq).execute().use { resp ->
                    val bodyStr = resp.body?.string()
                    val dataObj = try {
                        JSONObject(bodyStr ?: "{}").optJSONObject("data")
                    } catch (_: Exception) { null }
                    resp.isSuccessful && dataObj != null
                }
                if (!subExists) {
                    val json = JSONObject().apply {
                        put("username", username)
                        put("start_date", java.time.LocalDate.now().toString())
                        put("is_active", false)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val postReq = Request.Builder()
                        .url("https://papiqo.com/api/premium-subscriptions")
                        .header("Authorization", "Bearer $token")
                        .post(body)
                        .build()
                    client.newCall(postReq).execute().close()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getLogFileForUser(user: String): File {
        return File(requireContext().filesDir, "instalog_${user}.txt")
    }

    private fun loadSavedLogs(user: String) {
        logContainer.removeAllViews()
        val file = getLogFileForUser(user)
        if (file.exists()) {
            file.forEachLine { line ->
                appendLog(line, appendToFile = false)
            }
        }
    }

    private fun appendLog(
        text: String,
        appendToFile: Boolean = true,
        animate: Boolean = false
    ) {
        val tv = TextView(requireContext()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
        }
        logContainer.addView(tv)
        CoroutineScope(Dispatchers.Main).launch {
            if (animate) {
                for (c in text) {
                    tv.append(c.toString())
                    delay(30)
                }
            } else {
                tv.text = text
            }
            logScroll.fullScroll(View.FOCUS_DOWN)
        }
        if (appendToFile) {
            currentUsername?.let { user ->
                try {
                    getLogFileForUser(user).appendText(text + "\n")
                } catch (_: Exception) {
                    // ignore I/O errors
                }
            }
        }
    }

    private fun fetchTargetAccount() {
        if (token.isBlank() || userId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val userReq = Request.Builder()
                .url("https://papiqo.com/api/users/$userId")
                .header("Authorization", "Bearer $token")
                .build()
            try {
                client.newCall(userReq).execute().use { resp ->
                    val body = resp.body?.string()
                    val clientId = if (resp.isSuccessful) {
                        try {
                            JSONObject(body ?: "{}")
                                .optJSONObject("data")
                                ?.optString("client_id") ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                    if (clientId.isNotBlank()) {
                        val insta = fetchClientInsta(client, clientId)
                        if (!insta.isNullOrBlank()) {
                            withContext(Dispatchers.Main) { targetUsername = insta }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun fetchClientInsta(client: OkHttpClient, clientId: String): String? {
        val req = Request.Builder()
            .url("https://papiqo.com/api/clients/$clientId")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                try {
                    JSONObject(body ?: "{}")
                        .optJSONObject("data")
                        ?.optString("client_insta")
                        ?.takeIf { it.isNotBlank() }
                        ?: JSONObject(body ?: "{}").optString("client_insta")
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchTodayPosts(doLike: Boolean, doRepost: Boolean, doComment: Boolean) {
        appendLog(
            ">>> Booting IG automation engine...",
            animate = true
        )
        appendLog(
            ">>> Target locked: @$targetUsername :: initializing recon...",
            animate = true
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = IGClient.deserialize(clientFile, cookieFile)
                val userAction = client.actions().users().findByUsername(targetUsername).join()
                val user = userAction.user
                val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(user.pk)
                val resp = client.sendRequest(req).join()
                val today = java.time.LocalDate.now()
                val zone = java.time.ZoneId.systemDefault()
                val posts = mutableListOf<PostInfo>()
                for (item in resp.items) {
                    val date = java.time.Instant.ofEpochSecond(item.taken_at)
                        .atZone(zone).toLocalDate()
                    if (date == today) {
                        val caption = item.caption?.text
                        var isVideo = false
                        var videoUrl: String? = null
                        var coverUrl: String? = null
                        val images = mutableListOf<String>()
                        when (item) {
                            is TimelineVideoMedia -> {
                                isVideo = true
                                videoUrl = item.video_versions?.firstOrNull()?.url
                                coverUrl = item.image_versions2?.candidates?.firstOrNull()?.url
                            }
                            is TimelineImageMedia -> {
                                item.image_versions2?.candidates?.firstOrNull()?.url?.let { images.add(it) }
                            }
                            is TimelineCarouselMedia -> {
                                for (c in item.carousel_media) {
                                    when (c) {
                                        is ImageCarouselItem -> c.image_versions2.candidates.firstOrNull()?.url?.let { images.add(it) }
                                        is VideoCarouselItem -> {
                                            isVideo = true
                                            if (videoUrl == null) videoUrl = c.video_versions?.firstOrNull()?.url
                                            if (coverUrl == null) coverUrl = c.image_versions2.candidates.firstOrNull()?.url
                                        }
                                    }
                                }
                            }
                        }
                        posts.add(
                            PostInfo(
                                code = item.code,
                                id = item.id,
                                caption = caption,
                                isVideo = isVideo,
                                imageUrls = images,
                                videoUrl = videoUrl,
                                coverUrl = coverUrl
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    launchLogAndLikes(client, posts, doLike, doRepost, doComment)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error: ${e.message}")
                }
            }
        }
    }

    private fun launchLogAndLikes(
        client: IGClient,
        posts: List<PostInfo>,
        doLike: Boolean,
        doRepost: Boolean,
        doComment: Boolean
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            for (post in posts) {
                val code = post.code
                appendLog(
                    "> found post: https://instagram.com/p/$code",
                    animate = true
                )
                delay(500)
            }
            appendLog(
                ">>> Scrape complete.",
                animate = true
            )

            if (doLike) {
                appendLog(
                    ">>> Preparing like sequence...",
                    animate = true
                )
                delay(2000)
                appendLog(
                    ">>> Executing like routine",
                    animate = true
                )
                var liked = 0
                for (post in posts) {
                    val code = post.code
                    val id = post.id
                    appendLog(
                        "> checking like status for $code",
                        animate = true
                    )

                    var alreadyLiked = false

                    try {
                        val info = withContext(Dispatchers.IO) {
                            client.sendRequest(
                                com.github.instagram4j.instagram4j.requests.media.MediaInfoRequest(id)
                            ).join()
                        }
                        val already = info.items.firstOrNull()?.isHas_liked == true
                        alreadyLiked = already
                        val statusText = if (already) "already liked" else "not yet liked"

                        appendLog(
                            "> status: $statusText",
                            animate = true
                        )
                    } catch (e: Exception) {
                        appendLog(
                            "Error checking like: ${e.message}",
                            animate = true
                        )
                    }

                    if (!alreadyLiked) {
                        try {
                            withContext(Dispatchers.IO) {
                                client.sendRequest(
                                    MediaActionRequest(id, MediaActionRequest.MediaAction.LIKE)
                                ).join()
                            }
                            appendLog(
                                "> liked post [$code]",
                                animate = true
                            )
                            liked++
                        } catch (e: Exception) {
                            appendLog("Error liking: ${'$'}{e.message}")
                        }
                    }
                    delay(1000)
                }
                appendLog(
                    ">>> Like routine finished. ${'$'}liked posts liked.",
                    animate = true
                )
            }

            if (doComment) {
                if (doLike) {
                    delay(10000)
                }
                appendLog(
                    ">>> Preparing comment sequence...",
                    animate = true
                )
                delay(2000)
                appendLog(
                    ">>> Executing comment routine",
                    animate = true
                )
                var commented = 0
                for (post in posts) {
                    val code = post.code
                    val id = post.id
                    val text = withContext(Dispatchers.IO) { fetchRandomQuote() } ?: ""
                    if (text.isBlank()) {
                        delay(1000)
                        continue
                    }
                    try {
                        withContext(Dispatchers.IO) {
                            client.sendRequest(
                                com.github.instagram4j.instagram4j.requests.media.MediaCommentRequest(id, text)
                            ).join()
                        }
                        appendLog(
                            "> commented on [$code]",
                            animate = true
                        )
                        commented++
                    } catch (e: Exception) {
                        appendLog("Error commenting: ${'$'}{e.message}")
                    }
                    delay(1000)
                }
                appendLog(
                    ">>> Comment routine finished. ${'$'}commented posts commented.",
                    animate = true
                )
            }

            if (doRepost) {
                if (doLike) {
                    delay(10000)
                }
                appendLog(
                    ">>> Initiating environment for re-post ops...",
                    animate = true
                )
                launchRepostSequence(client, posts)
            }
        }
    }

    private fun launchRepostSequence(client: IGClient, posts: List<PostInfo>) {
        CoroutineScope(Dispatchers.Main).launch {
            for (post in posts) {
                val files = withContext(Dispatchers.IO) { downloadMedia(post) }
                if (files.isEmpty()) continue
                try {
                    var newLink: String? = null
                    withContext(Dispatchers.IO) {
                        val response = if (post.isVideo && post.videoUrl != null) {
                            val video = files.first { it.extension == "mp4" }
                            val cover = files.firstOrNull { it.extension != "mp4" } ?: video
                            client.actions().timeline().uploadVideo(video, cover, post.caption ?: "").join()
                        } else {
                            if (files.size == 1) {
                                client.actions().timeline().uploadPhoto(files[0], post.caption ?: "").join()
                            } else {
                                val infos = files.map { TimelineAction.SidecarPhoto.from(it) }
                                client.actions().timeline().uploadAlbum(infos, post.caption ?: "").join()
                            }
                        }
                        newLink = response.media?.code?.let { "https://instagram.com/p/$it" }
                    }
                    newLink?.let {
                        appendLog("> repost link: $it", animate = true)
                        withContext(Dispatchers.IO) { sendRepostLink(post.code, it) }
                    }
                    withContext(Dispatchers.IO) { files.forEach { it.delete() } }
                } catch (e: Exception) {
                    appendLog("Error uploading: ${e.message}")
                }
                delay(60000)
            }
            appendLog(
                ">>> Repost routine complete.",
                animate = true
            )
        }
    }

    private fun sendRepostLink(sc: String, link: String) {
        if (token.isBlank() || userId.isBlank()) return
        val json = JSONObject().apply {
            put("shortcode", sc)
            put("user_id", userId)
            put("instagram_link", link)
            put("facebook_link", JSONObject.NULL)
            put("twitter_link", JSONObject.NULL)
            put("tiktok_link", JSONObject.NULL)
            put("youtube_link", JSONObject.NULL)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/link-reports")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            client.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private fun downloadMedia(post: PostInfo): List<File> {
        val dir = File(requireContext().getExternalFilesDir(null), "CiceroReposterApp")
        if (!dir.exists()) dir.mkdirs()
        val files = mutableListOf<File>()
        if (post.isVideo && post.videoUrl != null) {
            val videoFile = File(dir, post.code + ".mp4")
            if (!videoFile.exists()) {
                downloadUrl(post.videoUrl, videoFile)
            }
            files.add(videoFile)
            val coverUrl = post.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                val coverFile = File(dir, post.code + "_cover.jpg")
                if (!coverFile.exists()) downloadUrl(coverUrl, coverFile)
                files.add(coverFile)
            }
        } else {
            var idx = 1
            for (url in post.imageUrls) {
                val name = if (post.imageUrls.size > 1) "${post.code}_${idx++}.jpg" else "${post.code}.jpg"
                val f = File(dir, name)
                if (!f.exists()) downloadUrl(url, f)
                files.add(f)
            }
        }
        return files
    }

    private fun downloadUrl(url: String, file: File) {
        try {
            val client = OkHttpClient()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
                val body = resp.body ?: return
                file.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun fetchRandomQuote(): String? {
        if (token.isBlank()) return null
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/quotes/random")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                val obj = try {
                    JSONObject(body ?: "{}").optJSONObject("data")
                } catch (_: Exception) { null }
                obj?.optString("translation")?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun showTiktokDialog() {
        val input = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle("TikTok Username")
            .setView(input)
            .setPositiveButton("Login") { _, _ ->
                val username = input.text.toString().trim()
                if (username.isNotEmpty()) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val user = TikwmApi.fetchUser(username)
                        if (user != null) {
                            TiktokSessionManager.saveProfile(requireContext(), user)
                            Toast.makeText(requireContext(), "Logged in as ${user.optString("uniqueId")}", Toast.LENGTH_SHORT).show()
                            updateTiktokStatus()
                        } else {
                            Toast.makeText(requireContext(), "Login gagal", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_insta_profile, menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (clientFile.exists() && cookieFile.exists()) {
                            val client = IGClient.deserialize(clientFile, cookieFile)
                            client.sendRequest(AccountsLogoutRequest()).join()
                        }
                    } catch (_: Exception) {
                    }
                    withContext(Dispatchers.Main) {
                        profileContainer.visibility = View.GONE
                        loginContainer.visibility = View.VISIBLE
                        currentUsername = null
                        clientFile.delete()
                        cookieFile.delete()
                    }
                }
                true
            }
            R.id.action_register_premium -> {
                val intent = android.content.Intent(requireContext(), PremiumRegistrationActivity::class.java)
                intent.putExtra("username", currentUsername ?: "")
                startActivity(intent)
                true
            }
            R.id.action_confirm_subscription -> {
                val intent = android.content.Intent(requireContext(), SubscriptionConfirmActivity::class.java)
                intent.putExtra("username", currentUsername ?: "")
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
