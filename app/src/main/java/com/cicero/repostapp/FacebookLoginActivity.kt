package com.cicero.repostapp

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FacebookLoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facebook_login)

        val webView = findViewById<WebView>(R.id.webview_facebook)
        val progressBar = findViewById<ProgressBar>(R.id.progress_facebook)
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)

        FacebookSessionManager.loadCookies(this)?.let { saved ->
            manager.setCookie("https://facebook.com", saved)
            manager.setCookie("https://m.facebook.com", saved)
            manager.flush()
            setResult(RESULT_OK)
            finish()
            return
        }

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = android.view.View.GONE
                val cookies = manager.getCookie("https://m.facebook.com")
                val loggedIn = !cookies.isNullOrBlank() && cookies.contains("c_user=")
                if (loggedIn && url != null && url.contains("/me")) {
                    FacebookSessionManager.saveCookies(this@FacebookLoginActivity, cookies)
                    Toast.makeText(this@FacebookLoginActivity, "Login berhasil", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@FacebookLoginActivity, error?.description ?: error.toString(), Toast.LENGTH_SHORT).show()
            }
        }
        webView.loadUrl("https://m.facebook.com/login.php?next=https%3A%2F%2Fm.facebook.com%2Fme")
    }
}
