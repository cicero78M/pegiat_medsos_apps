package com.cicero.repostapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class YouTubeLoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_login)
        webView = findViewById(R.id.webview)

        val authUrl = (
            "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + BuildConfig.YOUTUBE_CLIENT_ID +
                "&redirect_uri=" + BuildConfig.YOUTUBE_REDIRECT_URI +
                "&response_type=token" +
                "&scope=https://www.googleapis.com/auth/youtube.readonly"
            )

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && url.startsWith(BuildConfig.YOUTUBE_REDIRECT_URI)) {
                    handleCallback(url)
                    return true
                }
                return false
            }
        }
        webView.loadUrl(authUrl)
    }

    private fun handleCallback(url: String) {
        val uri = Uri.parse(url)
        val fragment = uri.fragment ?: ""
        val token = fragment.split('&').firstOrNull { it.startsWith("access_token=") }?.substringAfter('=')
        if (token != null) {
            val intent = Intent().apply { putExtra("token", token) }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}
