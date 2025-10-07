package com.cicero.repostapp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {
    private const val AUTH_PREF_NAME = "auth_secure"
    private const val LEGACY_AUTH_PREF_NAME = "auth"

    fun getAuthPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPrefs = EncryptedSharedPreferences.create(
            appContext,
            AUTH_PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        migrateLegacyAuthPrefs(appContext, encryptedPrefs)
        return encryptedPrefs
    }

    private fun migrateLegacyAuthPrefs(
        context: Context,
        encryptedPrefs: SharedPreferences
    ) {
        val legacyPrefs = context.getSharedPreferences(LEGACY_AUTH_PREF_NAME, Context.MODE_PRIVATE)
        if (legacyPrefs.all.isEmpty()) {
            return
        }

        val legacyToken = legacyPrefs.getString("token", null)
        val legacyUserId = legacyPrefs.getString("userId", null)

        if (!legacyToken.isNullOrBlank() || !legacyUserId.isNullOrBlank()) {
            encryptedPrefs.edit {
                if (!legacyToken.isNullOrBlank()) {
                    putString("token", legacyToken)
                } else {
                    remove("token")
                }
                if (!legacyUserId.isNullOrBlank()) {
                    putString("userId", legacyUserId)
                } else {
                    remove("userId")
                }
            }
        }

        legacyPrefs.edit { clear() }
    }
}
