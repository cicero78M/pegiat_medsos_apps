package com.cicero.repostapp

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.fragment.app.Fragment

class FacebookFragment : Fragment(R.layout.fragment_facebook) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val webView: WebView = view.findViewById(R.id.web_facebook)
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://m.facebook.com/")
    }
}
