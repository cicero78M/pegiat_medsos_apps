package com.cicero.repostapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class FacebookLoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facebook_login)
        webView = findViewById(R.id.webview)

        val ua = "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36"
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = ua
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookie = cookieManager.getCookie("https://m.facebook.com") ?: return
                val userId = cookie.split(';').firstOrNull { it.trim().startsWith("c_user=") }
                    ?.substringAfter('=')
                if (userId != null) {
                    val intent = Intent().apply {
                        putExtra("userId", userId)
                        putExtra("cookie", cookie)
                    }
                    setResult(Activity.RESULT_OK, intent)
                    cookieManager.flush()
                    finish()
                }
            }
        }

        webView.loadUrl("https://m.facebook.com/login.php")
    }
}
