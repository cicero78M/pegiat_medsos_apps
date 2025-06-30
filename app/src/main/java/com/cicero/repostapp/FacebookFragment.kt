package com.cicero.repostapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class FacebookFragment : Fragment(R.layout.fragment_facebook) {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val statusView: TextView = view.findViewById(R.id.text_facebook_status)
        val loginButton: MaterialButton = view.findViewById(R.id.button_facebook_login)
        val webView: WebView = view.findViewById(R.id.webview_facebook)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val saved = FacebookSessionManager.loadCookies(requireContext())
        if (saved != null) {
            cookieManager.setCookie("https://facebook.com", saved)
            cookieManager.setCookie("https://basic.facebook.com", saved)
            cookieManager.flush()
            loginButton.text = getString(R.string.logout)
            loginButton.setOnClickListener { logout(statusView, loginButton, cookieManager) }
            fetchProfile(statusView)
        } else {
            loginButton.setOnClickListener { startLogin(webView, statusView, loginButton, cookieManager) }
        }
    }

    private fun logout(statusView: TextView, loginButton: MaterialButton, manager: CookieManager) {
        FacebookSessionManager.clear(requireContext())
        manager.removeAllCookies(null)
        manager.flush()
        statusView.text = getString(R.string.not_logged_in)
        loginButton.text = getString(R.string.login_facebook)
        loginButton.setOnClickListener {
            val webView = requireView().findViewById<WebView>(R.id.webview_facebook)
            startLogin(webView, statusView, loginButton, manager)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin(webView: WebView, statusView: TextView, loginButton: MaterialButton, manager: CookieManager) {
        webView.settings.javaScriptEnabled = true
        webView.visibility = View.VISIBLE
        loginButton.visibility = View.GONE
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && url.contains("/me")) {
                    val cookies = manager.getCookie("https://basic.facebook.com")
                    if (!cookies.isNullOrBlank()) {
                        FacebookSessionManager.saveCookies(requireContext(), cookies)
                    }
                    webView.visibility = View.GONE
                    loginButton.visibility = View.VISIBLE
                    loginButton.text = getString(R.string.logout)
                    loginButton.setOnClickListener { logout(statusView, loginButton, manager) }
                    fetchProfile(statusView)
                }
            }
        }
        webView.loadUrl("https://basic.facebook.com/login.php?next=https%3A%2F%2Fbasic.facebook.com%2Fme")
    }

    private fun fetchProfile(statusView: TextView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cookies = FacebookSessionManager.loadCookies(requireContext()) ?: return@launch
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("https://basic.facebook.com/me")
                    .header("Cookie", cookies)
                    .header("User-Agent", "Mozilla/5.0 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val name = Regex("<title>([^<]+)").find(body)?.groupValues?.get(1) ?: "Facebook"
                    withContext(Dispatchers.Main) { statusView.text = name }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
