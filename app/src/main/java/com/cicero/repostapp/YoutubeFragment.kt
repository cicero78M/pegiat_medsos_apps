package com.cicero.repostapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton

class YoutubeFragment : Fragment(R.layout.fragment_youtube) {
    private lateinit var client: GoogleSignInClient
    private lateinit var statusView: TextView
    private lateinit var loginButton: MaterialButton
    private lateinit var logoutButton: MaterialButton

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
        loginButton = view.findViewById(R.id.button_youtube_login)
        logoutButton = view.findViewById(R.id.button_youtube_logout)

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/youtube.readonly"))
            .build()
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
        loginButton.visibility = View.GONE
        logoutButton.visibility = View.VISIBLE
    }

    private fun showLoggedOut() {
        statusView.text = getString(R.string.not_logged_in)
        loginButton.visibility = View.VISIBLE
        logoutButton.visibility = View.GONE
    }
}
