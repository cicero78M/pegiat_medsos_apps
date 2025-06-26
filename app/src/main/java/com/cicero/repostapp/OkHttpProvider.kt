package com.cicero.repostapp

import android.content.Context
import okhttp3.OkHttpClient

object OkHttpProvider {
    @Volatile
    private var client: OkHttpClient? = null

    fun getClient(context: Context): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: OkHttpClient.Builder()
                .cookieJar(PersistentCookieJar(context.applicationContext))
                .build().also { client = it }
        }
    }
}
