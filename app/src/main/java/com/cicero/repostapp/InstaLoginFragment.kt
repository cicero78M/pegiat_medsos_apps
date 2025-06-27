package com.cicero.repostapp

import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.IGClient.Builder.LoginHandler
import com.github.instagram4j.instagram4j.utils.IGChallengeUtils
import com.github.instagram4j.instagram4j.exceptions.IGLoginException
import java.util.concurrent.Callable

class InstaLoginFragment : Fragment(R.layout.fragment_insta_login) {
    private lateinit var loginContainer: View
    private lateinit var profileContainer: View
    private lateinit var avatarView: ImageView
    private lateinit var usernameView: TextView
    private lateinit var nameView: TextView
    private lateinit var postsView: TextView
    private lateinit var followersView: TextView
    private lateinit var followingView: TextView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val username = view.findViewById<EditText>(R.id.input_username)
        val password = view.findViewById<EditText>(R.id.input_password)
        loginContainer = view.findViewById(R.id.login_container)
        profileContainer = view.findViewById(R.id.profile_container)
        avatarView = profileContainer.findViewById(R.id.image_avatar)
        usernameView = profileContainer.findViewById(R.id.text_username)
        nameView = profileContainer.findViewById(R.id.text_name)
        postsView = profileContainer.findViewById(R.id.stat_posts)
        followersView = profileContainer.findViewById(R.id.stat_followers)
        followingView = profileContainer.findViewById(R.id.stat_following)
        profileContainer.findViewById<View>(R.id.text_nrp).visibility = View.GONE
        profileContainer.findViewById<View>(R.id.info_container).visibility = View.GONE

        profileContainer.findViewById<Button>(R.id.button_logout).setOnClickListener {
            profileContainer.visibility = View.GONE
            loginContainer.visibility = View.VISIBLE
        }

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
                runBlocking { promptCode("Masukkan kode verifikasi") }
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
                val info = client.actions().users().info(client.selfProfile.pk).join()
                withContext(Dispatchers.Main) {
                    usernameView.text = "@${info.username}"
                    nameView.text = info.full_name ?: ""
                    postsView.text = info.media_count.toString()
                    followersView.text = info.follower_count.toString()
                    followingView.text = info.following_count.toString()
                    Glide.with(this@InstaLoginFragment)
                        .load(info.profile_pic_url)
                        .circleCrop()
                        .into(avatarView)
                    loginContainer.visibility = View.GONE
                    profileContainer.visibility = View.VISIBLE
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

    private suspend fun promptCode(title: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val input = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(input)
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
}
