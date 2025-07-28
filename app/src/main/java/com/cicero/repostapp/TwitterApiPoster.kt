package com.cicero.repostapp

import android.util.Log
import twitter4j.HttpParameter
import twitter4j.HttpRequest
import twitter4j.OAuthAuthorization
import twitter4j.RequestMethod
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import com.cicero.repostapp.BuildConfig

private val API_KEY get() = BuildConfig.TWITTER_CONSUMER_KEY
private val API_SECRET get() = BuildConfig.TWITTER_CONSUMER_SECRET
private val ACCESS_TOKEN get() = BuildConfig.TWITTER_ACCESS_TOKEN
private val ACCESS_TOKEN_SECRET get() = BuildConfig.TWITTER_ACCESS_SECRET

suspend fun postTweetWithMedia(tweetText: String, file: File): Boolean {
    val tag = "TwitterApiPoster"
    val auth = OAuthAuthorization.newBuilder()
        .oAuthConsumer(API_KEY, API_SECRET)
        .oAuthAccessToken(ACCESS_TOKEN, ACCESS_TOKEN_SECRET)
        .build()
    val client = OkHttpClient()

    Log.d(tag, "Uploading media…")
    val mediaBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "media",
            file.name,
            file.asRequestBody("application/octet-stream".toMediaType())
        )
        .build()

    val uploadReq = HttpRequest(
        RequestMethod.POST,
        "https://upload.twitter.com/1.1/media/upload.json",
        emptyArray(),
        auth,
        emptyMap()
    )
    val uploadRequest = Request.Builder()
        .url("https://upload.twitter.com/1.1/media/upload.json")
        .header("Authorization", auth.getAuthorizationHeader(uploadReq))
        .post(mediaBody)
        .build()

    val mediaId = client.newCall(uploadRequest).execute().use { resp ->
        if (!resp.isSuccessful) {
            Log.e(tag, "Upload failed: ${'$'}{resp.code}")
            return false
        }
        val body = resp.body?.string() ?: ""
        Log.d(tag, "Upload response: ${'$'}body")
        JSONObject(body).optString("media_id_string")
    }

    if (mediaId.isBlank()) {
        Log.e(tag, "No media id returned")
        return false
    }

    Log.d(tag, "Posting tweet with media ${'$'}mediaId")
    val formBody = FormBody.Builder()
        .add("status", tweetText)
        .add("media_ids", mediaId)
        .build()

    val tweetReq = HttpRequest(
        RequestMethod.POST,
        "https://api.twitter.com/1.1/statuses/update.json",
        arrayOf(
            HttpParameter("status", tweetText),
            HttpParameter("media_ids", mediaId)
        ),
        auth,
        emptyMap()
    )

    val request = Request.Builder()
        .url("https://api.twitter.com/1.1/statuses/update.json")
        .header("Authorization", auth.getAuthorizationHeader(tweetReq))
        .post(formBody)
        .build()

    return client.newCall(request).execute().use { resp ->
        if (resp.isSuccessful) {
            Log.d(tag, "Tweet posted successfully")
            true
        } else {
            Log.e(tag, "Tweet failed: ${'$'}{resp.code}")
            false
        }
    }
}
