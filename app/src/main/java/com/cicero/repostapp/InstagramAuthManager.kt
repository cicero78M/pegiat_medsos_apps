package com.cicero.repostapp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object InstagramAuthManager {
    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            "instagram",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun loadToken(context: Context): Pair<String, String>? {
        val p = prefs(context)
        val token = p.getString("token", null)
        val user = p.getString("user", null)
        return if (token != null && user != null) token to user else null
    }

    fun saveToken(context: Context, token: String, user: String) {
        prefs(context).edit().apply {
            putString("token", token)
            putString("user", user)
            apply()
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    suspend fun exchangeCode(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.IG_CLIENT_ID)
            .add("client_secret", BuildConfig.IG_CLIENT_SECRET)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", BuildConfig.IG_REDIRECT_URI)
            .add("code", code)
            .build()
        val req = Request.Builder()
            .url("https://api.instagram.com/oauth/access_token")
            .post(body)
            .build()
        return@withContext try {
            client.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "")
                val token = json.optString("access_token")
                val user = json.optJSONObject("user")?.optString("id")
                if (!token.isNullOrBlank() && !user.isNullOrBlank()) {
                    saveToken(context, token, user)
                    true
                } else {
                    false
                }
            }
        } catch (_: Exception) { false }
    }
}
