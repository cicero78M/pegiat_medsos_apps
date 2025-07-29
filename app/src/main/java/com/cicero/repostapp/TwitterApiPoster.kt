package com.cicero.repostapp

import android.util.Log
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.StatusUpdate
import twitter4j.conf.ConfigurationBuilder
import twitter4j.json.DataObjectFactory
import java.io.File
import com.cicero.repostapp.BuildConfig

private val API_KEY get() = BuildConfig.TWITTER_CONSUMER_KEY
private val API_SECRET get() = BuildConfig.TWITTER_CONSUMER_SECRET
private val ACCESS_TOKEN get() = BuildConfig.TWITTER_ACCESS_TOKEN
private val ACCESS_TOKEN_SECRET get() = BuildConfig.TWITTER_ACCESS_SECRET


suspend fun postTweetWithMedia(tweetText: String, file: File): Boolean {
    val tag = "TwitterApiPoster"
    val cb = ConfigurationBuilder()
        .setDebugEnabled(true)
        .setOAuthConsumerKey(API_KEY)
        .setOAuthConsumerSecret(API_SECRET)
        .setOAuthAccessToken(ACCESS_TOKEN)
        .setOAuthAccessTokenSecret(ACCESS_TOKEN_SECRET)
    val twitter = TwitterFactory(cb.build()).instance

    return try {
        val status = StatusUpdate(tweetText)
        status.setMedia(file)
        twitter.updateStatus(status)
        Log.d(tag, "Tweet posted successfully")
        true
    } catch (e: TwitterException) {
        Log.e(tag, "Tweet failed", e)
        false
    }
}

data class TwitterPostResult(val success: Boolean, val rawResponse: String)

suspend fun postTweetWithMediaResponse(tweetText: String, file: File): TwitterPostResult {
    val tag = "TwitterApiPoster"
    val cb = ConfigurationBuilder()
        .setDebugEnabled(true)
        .setOAuthConsumerKey(API_KEY)
        .setOAuthConsumerSecret(API_SECRET)
        .setOAuthAccessToken(ACCESS_TOKEN)
        .setOAuthAccessTokenSecret(ACCESS_TOKEN_SECRET)
        .setJSONStoreEnabled(true)
    val twitter = TwitterFactory(cb.build()).instance

    return try {
        val status = StatusUpdate(tweetText)
        status.setMedia(file)
        val resp = twitter.updateStatus(status)
        val raw = DataObjectFactory.getRawJSON(resp) ?: resp.toString()
        Log.d(tag, "Tweet posted successfully")
        TwitterPostResult(true, raw)
    } catch (e: TwitterException) {
        Log.e(tag, "Tweet failed", e)
        TwitterPostResult(false, e.errorMessage ?: e.toString())
    }
}
