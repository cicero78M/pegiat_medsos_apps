package com.cicero.repostapp

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val set = cookies.map { it.toString() }.toSet()
        prefs.edit().putStringSet(url.host, set).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = prefs.getStringSet(url.host, emptySet()) ?: emptySet()
        return stored.mapNotNull { Cookie.parse(url, it) }
    }
}
