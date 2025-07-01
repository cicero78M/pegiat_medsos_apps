package com.cicero.repostapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Bitmap
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import android.widget.ProgressBar
import com.bumptech.glide.Glide
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
        val avatarView: ImageView = view.findViewById(R.id.image_facebook_avatar)
        val loginButton: MaterialButton = view.findViewById(R.id.button_facebook_login)
        val webView: WebView = view.findViewById(R.id.webview_facebook)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_facebook)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val saved = FacebookSessionManager.loadCookies(requireContext())
        if (saved != null) {
            cookieManager.setCookie("https://facebook.com", saved)
            cookieManager.setCookie("https://m.facebook.com", saved)
            cookieManager.flush()
            loginButton.text = getString(R.string.logout)
            loginButton.setOnClickListener { logout(statusView, loginButton, cookieManager) }
            fetchProfile(statusView, avatarView)
        } else {
            loginButton.setOnClickListener { startLogin(webView, progressBar, statusView, avatarView, loginButton, cookieManager) }
        }
    }

    private fun logout(statusView: TextView, loginButton: MaterialButton, manager: CookieManager) {
        FacebookSessionManager.clear(requireContext())
        manager.removeAllCookies(null)
        manager.flush()
        statusView.text = getString(R.string.not_logged_in)
        val avatarView: ImageView = requireView().findViewById(R.id.image_facebook_avatar)
        avatarView.visibility = View.GONE
        loginButton.text = getString(R.string.login_facebook)
        loginButton.setOnClickListener {
            val webView = requireView().findViewById<WebView>(R.id.webview_facebook)
            val progressBar = requireView().findViewById<ProgressBar>(R.id.progress_facebook)
            startLogin(webView, progressBar, statusView, avatarView, loginButton, manager)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin(
        webView: WebView,
        progressBar: ProgressBar,
        statusView: TextView,
        avatarView: ImageView,
        loginButton: MaterialButton,
        manager: CookieManager
    ) {
        webView.settings.javaScriptEnabled = true
        webView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        loginButton.visibility = View.GONE
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                val cookies = manager.getCookie("https://m.facebook.com")
                val loggedIn = !cookies.isNullOrBlank() && cookies.contains("c_user=")
                if (loggedIn && url != null && url.contains("/me")) {
                    FacebookSessionManager.saveCookies(requireContext(), cookies)
                    webView.visibility = View.GONE
                    loginButton.visibility = View.VISIBLE
                    loginButton.text = getString(R.string.logout)
                    loginButton.setOnClickListener { logout(statusView, loginButton, manager) }
                    fetchProfile(statusView, avatarView)
                } else if (!loggedIn && url != null && url.contains("save-device")) {
                    Toast.makeText(requireContext(), R.string.login_failed, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), error?.description ?: error.toString(), Toast.LENGTH_SHORT).show()
            }
        }
        webView.loadUrl("https://m.facebook.com/login.php?next=https%3A%2F%2Fm.facebook.com%2Fme")
    }

    private fun fetchProfile(statusView: TextView, avatarView: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cookies = FacebookSessionManager.loadCookies(requireContext()) ?: return@launch
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("https://m.facebook.com/me")
                    .header("Cookie", cookies)
                    .header("User-Agent", "Mozilla/5.0 (Android)")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val name = Regex("<title>([^<]+)").find(body)?.groupValues?.get(1) ?: "Facebook"
                    val avatar = Regex("<img[^>]+src=\\"([^\\"]+)\\"[^>]*profile", RegexOption.IGNORE_CASE)
                        .find(body)?.groupValues?.get(1)
                        ?: Regex("profilePicThumb\\\"[^>]*src=\\\"([^\\"]+)\\\"", RegexOption.IGNORE_CASE)
                            .find(body)?.groupValues?.get(1)
                    withContext(Dispatchers.Main) {
                        statusView.text = name
                        if (avatar != null) {
                            avatarView.visibility = View.VISIBLE
                            Glide.with(this@FacebookFragment)
                                .load(avatar)
                                .placeholder(R.drawable.profile_avatar_placeholder)
                                .error(R.drawable.profile_avatar_placeholder)
                                .circleCrop()
                                .into(avatarView)
                        } else {
                            avatarView.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), e.message ?: e.toString(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
