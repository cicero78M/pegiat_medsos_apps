package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.material.button.MaterialButton

class FacebookFragment : Fragment(R.layout.fragment_facebook) {
    private lateinit var statusView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var loginButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var callbackManager: CallbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callbackManager = CallbackManager.Factory.create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusView = view.findViewById(R.id.text_facebook_status)
        avatarView = view.findViewById(R.id.image_facebook_avatar)
        loginButton = view.findViewById(R.id.button_facebook_login)
        progressBar = view.findViewById(R.id.progress_facebook)
        refreshStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val token = AccessToken.getCurrentAccessToken()
        if (token != null && !token.isExpired) {
            loginButton.text = getString(R.string.logout)
            loginButton.setOnClickListener {
                LoginManager.getInstance().logOut()
                refreshStatus()
            }
            fetchProfile(token)
        } else {
            statusView.text = getString(R.string.not_logged_in)
            avatarView.visibility = View.GONE
            loginButton.text = getString(R.string.login_facebook)
            loginButton.setOnClickListener { startLogin() }
        }
    }

    private fun startLogin() {
        progressBar.visibility = View.VISIBLE
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                progressBar.visibility = View.GONE
                refreshStatus()
            }

            override fun onCancel() {
                progressBar.visibility = View.GONE
            }

            override fun onError(error: FacebookException) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), error.message ?: error.toString(), Toast.LENGTH_SHORT).show()
            }
        })
        LoginManager.getInstance().logInWithReadPermissions(this, listOf("public_profile"))
    }

    private fun fetchProfile(token: AccessToken) {
        progressBar.visibility = View.VISIBLE
        val request = GraphRequest.newMeRequest(token) { obj, _ ->
            progressBar.visibility = View.GONE
            val name = obj?.optString("name") ?: "Facebook"
            val avatar = obj?.optJSONObject("picture")?.optJSONObject("data")?.optString("url")
            statusView.text = name
            if (avatar != null) {
                avatarView.visibility = View.VISIBLE
                Glide.with(this)
                    .load(avatar)
                    .placeholder(R.drawable.profile_avatar_placeholder)
                    .circleCrop()
                    .into(avatarView)
            } else {
                avatarView.visibility = View.GONE
            }
        }
        val params = android.os.Bundle()
        params.putString("fields", "name,picture.type(large)")
        request.parameters = params
        request.executeAsync()
    }
}
