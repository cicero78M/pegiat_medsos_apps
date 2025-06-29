package com.cicero.repostapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.auth.RequestToken
import twitter4j.conf.ConfigurationBuilder
import com.cicero.repostapp.BuildConfig

class TwitterFragment : Fragment(R.layout.fragment_twitter) {

    private var twitter: Twitter? = null
    private var requestToken: RequestToken? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loginButton: com.google.android.material.button.MaterialButton =
            view.findViewById(R.id.button_twitter_login)
        val verifyButton: com.google.android.material.button.MaterialButton =
            view.findViewById(R.id.button_twitter_verify)
        val pinInput: com.google.android.material.textfield.TextInputEditText =
            view.findViewById(R.id.input_twitter_pin)
        val pinLayout: com.google.android.material.textfield.TextInputLayout =
            view.findViewById(R.id.pin_layout)
        val statusView: android.widget.TextView = view.findViewById(R.id.text_twitter_status)

        val prefs = createEncryptedPrefs(requireContext())
        val accessToken = prefs.getString("token", null)
        val accessSecret = prefs.getString("secret", null)
        if (accessToken != null && accessSecret != null) {
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
                        statusView.text = "@${'$'}{user.screenName}"
                        loginButton.text = getString(R.string.logout)
                        loginButton.setOnClickListener {
                            prefs.edit { clear() }
                            statusView.text = getString(R.string.not_logged_in)
                            loginButton.text = getString(R.string.login_twitter)
                            loginButton.setOnClickListener { startLogin(prefs, pinLayout, pinInput, verifyButton, statusView) }
                        }
                    }
                } catch (_: TwitterException) {
                    withContext(Dispatchers.Main) {
                        statusView.text = getString(R.string.not_logged_in)
                        loginButton.setOnClickListener { startLogin(prefs, pinLayout, pinInput, verifyButton, statusView) }
                    }
                }
            }
        } else {
            loginButton.setOnClickListener { startLogin(prefs, pinLayout, pinInput, verifyButton, statusView) }
        }

        verifyButton.setOnClickListener {
            val pin = pinInput.text?.toString()?.trim().orEmpty()
            if (pin.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.enter_pin), Toast.LENGTH_SHORT).show()
            } else {
                finishLogin(pin, prefs, pinLayout, pinInput, verifyButton, statusView, loginButton)
            }
        }
    }

    private fun startLogin(
        prefs: SharedPreferences,
        pinLayout: com.google.android.material.textfield.TextInputLayout,
        pinInput: com.google.android.material.textfield.TextInputEditText,
        verifyButton: View,
        statusView: android.widget.TextView
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = ConfigurationBuilder()
                    .setOAuthConsumerKey(BuildConfig.TWITTER_CONSUMER_KEY)
                    .setOAuthConsumerSecret(BuildConfig.TWITTER_CONSUMER_SECRET)
                    .build()
                twitter = TwitterFactory(config).instance
                val reqToken = twitter?.oAuthRequestToken
                requestToken = reqToken
                val url = reqToken?.authorizationURL ?: return@launch
                withContext(Dispatchers.Main) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    statusView.text = getString(R.string.enter_pin)
                    pinInput.setText("")
                    pinLayout.visibility = View.VISIBLE
                    verifyButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "${'$'}{e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun finishLogin(
        pin: String,
        prefs: SharedPreferences,
        pinLayout: com.google.android.material.textfield.TextInputLayout,
        pinInput: com.google.android.material.textfield.TextInputEditText,
        verifyButton: View,
        statusView: android.widget.TextView,
        loginButton: com.google.android.material.button.MaterialButton
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val tw = twitter ?: return@launch
            val reqToken = requestToken ?: return@launch
            try {
                val token = tw.getOAuthAccessToken(reqToken, pin)
                prefs.edit {
                    putString("token", token.token)
                    putString("secret", token.tokenSecret)
                }
                val user = tw.verifyCredentials()
                withContext(Dispatchers.Main) {
                    statusView.text = "@${'$'}{user.screenName}"
                    pinLayout.visibility = View.GONE
                    verifyButton.visibility = View.GONE
                    loginButton.text = getString(R.string.logout)
                    loginButton.setOnClickListener {
                        prefs.edit { clear() }
                        statusView.text = getString(R.string.not_logged_in)
                        loginButton.text = getString(R.string.login_twitter)
                        loginButton.setOnClickListener { startLogin(prefs, pinLayout, pinInput, verifyButton, statusView) }
                    }
                    Toast.makeText(requireContext(), "Login berhasil", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "PIN salah", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "twitter",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
