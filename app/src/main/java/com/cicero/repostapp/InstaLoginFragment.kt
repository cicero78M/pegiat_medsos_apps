package com.cicero.repostapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.IGClient.Builder.LoginHandler
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import com.github.instagram4j.instagram4j.requests.media.MediaActionRequest
import java.io.File
import java.util.concurrent.Callable

class InstaLoginFragment : Fragment(R.layout.fragment_insta_login) {
    private lateinit var loginContainer: View
    private lateinit var profileContainer: View
    private lateinit var startButton: Button
    private lateinit var logContainer: android.widget.LinearLayout
    private lateinit var logScroll: android.widget.ScrollView
    private lateinit var avatarView: ImageView
    private lateinit var usernameView: TextView
    private lateinit var nameView: TextView
    private lateinit var bioView: TextView
    private lateinit var postsView: TextView
    private lateinit var followersView: TextView
    private lateinit var followingView: TextView
    private val clientFile: File by lazy { File(requireContext().filesDir, "igclient.ser") }
    private val cookieFile: File by lazy { File(requireContext().filesDir, "igcookie.ser") }
    private var currentUsername: String? = null

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

        startButton = view.findViewById(R.id.button_start)
        logContainer = view.findViewById(R.id.log_container)
        logScroll = view.findViewById(R.id.log_scroll)

        startButton.setOnClickListener { fetchTodayPosts() }

        restoreSession()

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
                    displayProfile(client, info)
                }
            } catch (e: IGLoginException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal login: ${e.loginResponse.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


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

    private fun displayProfile(client: IGClient, info: com.github.instagram4j.instagram4j.models.user.User?) {
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
    }

    private fun restoreSession() {
        CoroutineScope(Dispatchers.IO).launch {
            if (clientFile.exists() && cookieFile.exists()) {
                try {
                    val client = IGClient.deserialize(clientFile, cookieFile)
                    val info = client.actions().users().info(client.selfProfile.pk).join()
                    withContext(Dispatchers.Main) { displayProfile(client, info) }
                } catch (_: Exception) {
                    // ignore invalid session
                }
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

    private fun fetchTodayPosts() {
        appendLog(
            ">>> Booting IG automation engine...",
            animate = true
        )
        appendLog(
            ">>> Target locked: @polresbojonegoroofficial :: initializing recon...",
            animate = true
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = IGClient.deserialize(clientFile, cookieFile)
                val userAction = client.actions().users().findByUsername("polresbojonegoroofficial").join()
                val user = userAction.user
                val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(user.pk)
                val resp = client.sendRequest(req).join()
                val today = java.time.LocalDate.now()
                val zone = java.time.ZoneId.systemDefault()
                val posts = mutableListOf<Pair<String, String>>()
                for (item in resp.items) {
                    val date = java.time.Instant.ofEpochSecond(item.taken_at)
                        .atZone(zone).toLocalDate()
                    if (date == today) {
                        posts.add(item.code to item.id)
                    }
                }
                withContext(Dispatchers.Main) {
                    launchLogAndLikes(client, posts)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendLog("Error: ${e.message}")
                }
            }
        }
    }

    private fun launchLogAndLikes(client: IGClient, posts: List<Pair<String, String>>) {
        CoroutineScope(Dispatchers.Main).launch {
            for ((code, _) in posts) {
                appendLog(
                    "> found post: https://instagram.com/p/$code",
                    animate = true
                )
                delay(500)
            }
            appendLog(
                ">>> Scrape complete. Preparing like sequence...",
                animate = true
            )
            delay(2000)
            appendLog(
                ">>> Executing like routine",
                animate = true
            )
            var liked = 0
            for ((code, id) in posts) {
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
                    alreadyLiked = info.items.firstOrNull()?.isHas_liked == true
                    appendLog(
                        "> status: ${'$'}{if (alreadyLiked) "already liked" else "not yet liked"}",
                        animate = true
                    )
                } catch (e: Exception) {
                    appendLog("Error checking like: ${'$'}{e.message}")
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
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu, inflater: android.view.MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_insta_profile, menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return if (item.itemId == R.id.action_logout) {
            profileContainer.visibility = View.GONE
            loginContainer.visibility = View.VISIBLE
            currentUsername = null
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}
