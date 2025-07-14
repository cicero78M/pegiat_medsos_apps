package com.cicero.repostapp

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.auth.AccessToken
import twitter4j.auth.RequestToken

class TwitterLoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var twitter: Twitter
    private lateinit var requestToken: RequestToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_twitter_login)
        webView = findViewById(R.id.webview)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                twitter = TwitterFactory.getSingleton().apply {
                    setOAuthConsumer(BuildConfig.TWITTER_CONSUMER_KEY, BuildConfig.TWITTER_CONSUMER_SECRET)
                }
                requestToken = twitter.getOAuthRequestToken(BuildConfig.TWITTER_CALLBACK_URL)
            }
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && url.startsWith(BuildConfig.TWITTER_CALLBACK_URL)) {
                        handleCallback(Uri.parse(url))
                        return true
                    }
                    return false
                }
            }
            webView.loadUrl(requestToken.authorizationURL)
        }
    }

    private fun handleCallback(uri: Uri) {
        val verifier = uri.getQueryParameter("oauth_verifier") ?: return finish()
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) {
                twitter.getOAuthAccessToken(requestToken, verifier)
            }
            val user = withContext(Dispatchers.IO) { twitter.verifyCredentials() }
            val intent = Intent().apply {
                putExtra("token", token.token)
                putExtra("secret", token.tokenSecret)
                putExtra("profile", user.profileImageURLHttps)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}
