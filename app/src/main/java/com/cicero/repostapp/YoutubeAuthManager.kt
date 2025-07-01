package com.cicero.repostapp

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object YoutubeAuthManager {
    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            "youtube",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveAccount(context: Context, email: String?, name: String?, photo: String?) {
        prefs(context).edit(commit = true) {
            putString("email", email)
            putString("name", name)
            putString("photo", photo)
        }
    }

    fun loadAccount(context: Context): Triple<String?, String?, String?>? {
        val p = prefs(context)
        val email = p.getString("email", null)
        val name = p.getString("name", null)
        val photo = p.getString("photo", null)
        return if (email != null || name != null || photo != null) Triple(email, name, photo) else null
    }

    fun clear(context: Context) {
        prefs(context).edit(commit = true) { clear() }
    }
}
