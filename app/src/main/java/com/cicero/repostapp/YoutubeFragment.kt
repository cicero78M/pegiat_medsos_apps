package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.cicero.repostapp.BuildConfig

class YoutubeFragment : Fragment(R.layout.fragment_youtube) {
    private lateinit var client: GoogleSignInClient
    private lateinit var statusView: TextView
    private lateinit var resultView: TextView
    private lateinit var loginButton: MaterialButton
    private lateinit var logoutButton: MaterialButton

    private val youtubeClientId = BuildConfig.YOUTUBE_CLIENT_ID
    private val youtubeApiKey = BuildConfig.YOUTUBE_API_KEY
    private val youtubeClientSecret = BuildConfig.YOUTUBE_CLIENT_SECRET

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            onSignedIn(account)
        } catch (e: Exception) {
            statusView.text = getString(R.string.login_failed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusView = view.findViewById(R.id.text_youtube_status)
        resultView = view.findViewById(R.id.text_youtube_result)
        loginButton = view.findViewById(R.id.button_youtube_login)
        logoutButton = view.findViewById(R.id.button_youtube_logout)

        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/youtube.readonly"))
        if (youtubeClientId.isNotBlank()) {
            builder.requestIdToken(youtubeClientId)
            builder.requestServerAuthCode(youtubeClientId)
        }
        val options = builder.build()
        client = GoogleSignIn.getClient(requireContext(), options)

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null) {
            onSignedIn(account)
        } else {
            showLoggedOut()
        }

        loginButton.setOnClickListener {
            launcher.launch(client.signInIntent)
        }

        logoutButton.setOnClickListener {
            client.signOut().addOnCompleteListener {
                YoutubeAuthManager.clear(requireContext())
                showLoggedOut()
            }
        }
    }

    private fun onSignedIn(account: GoogleSignInAccount) {
        val email = account.email
        val name = account.displayName
        val photo = account.photoUrl?.toString()
        YoutubeAuthManager.saveAccount(requireContext(), email, name, photo)
        statusView.text = email ?: name ?: getString(R.string.not_logged_in)
        resultView.text = getString(R.string.loading)
        loginButton.visibility = View.GONE
        logoutButton.visibility = View.VISIBLE
        fetchProfile(account)
    }

    private fun showLoggedOut() {
        statusView.text = getString(R.string.not_logged_in)
        resultView.text = ""
        loginButton.visibility = View.VISIBLE
        logoutButton.visibility = View.GONE
    }

    private fun fetchProfile(account: GoogleSignInAccount) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = GoogleAuthUtil.getToken(
                    requireContext(),
                    account.account!!,
                    "oauth2:https://www.googleapis.com/auth/youtube.readonly"
                )
                try {
                    val client = OkHttpClient()
                    val req = Request.Builder()
                        .url(
                            "https://www.googleapis.com/youtube/v3/channels" +
                                "?part=snippet&mine=true&key=$youtubeApiKey"
                        )
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        withContext(Dispatchers.Main) {
                            resultView.text = body
                        }
                    }
                } finally {
                    GoogleAuthUtil.clearToken(requireContext(), token)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultView.text = e.message ?: e.toString()
                }
            }
        }
    }
}
