package com.cicero.repostapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService to automate composing tweet using stored text.
 */
class TwitterAutoPostService : AccessibilityService() {

    override fun onServiceConnected() {
        // Configure the service to listen to Twitter app only
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.twitter.android")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.twitter.android") return
        val root = rootInActiveWindow ?: return
        val prefs = getSharedPreferences("twitter_post_prefs", MODE_PRIVATE)
        val text = prefs.getString("twitter_post_text", null) ?: return

        // 1. open compose tweet
        val fab = root.findAccessibilityNodeInfosByViewId("com.twitter.android:id/fab").firstOrNull()
        if (fab != null) {
            fab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // 2. fill tweet text
        val tweetField = root.findAccessibilityNodeInfosByViewId("com.twitter.android:id/tweet_text").firstOrNull()
        if (tweetField != null && tweetField.text.isNullOrEmpty()) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            tweetField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return
        }

        // 3. send tweet
        val tweetButton = root.findAccessibilityNodeInfosByText("Tweet").firstOrNull()
        if (tweetButton != null && tweetField != null && tweetField.text?.toString() == text) {
            tweetButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // Clear stored text after posting
            prefs.edit().clear().apply()
            // TODO: capture tweet link and report to server
        }
    }

    override fun onInterrupt() {}
}
