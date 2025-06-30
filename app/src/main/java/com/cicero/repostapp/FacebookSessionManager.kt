package com.cicero.repostapp

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object FacebookSessionManager {
    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            "facebook",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveCookies(context: Context, cookies: String) {
        prefs(context).edit { putString("cookies", cookies) }
    }

    fun loadCookies(context: Context): String? {
        return prefs(context).getString("cookies", null)
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}
