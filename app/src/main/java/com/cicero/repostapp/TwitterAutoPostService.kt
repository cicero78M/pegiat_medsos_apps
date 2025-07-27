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
        // Listen for events from all apps to handle share sheet as well
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return

        // Handle possible share sheet asking for default action
        val shareTitle = root.findAccessibilityNodeInfosByText("Bagikan").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Share").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Open with").firstOrNull()
        val remember = root.findAccessibilityNodeInfosByText("Ingat Pilihan saya").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Ingat pilihan saya").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Always").firstOrNull()
        if (shareTitle != null && remember != null) {
            if (remember.isCheckable && !remember.isChecked) {
                remember.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            val posting = root.findAccessibilityNodeInfosByText("Posting").firstOrNull()
                ?: root.findAccessibilityNodeInfosByText("Twitter").firstOrNull()
            posting?.let { node ->
                (node.parent ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            return
        }

        if (event?.packageName != "com.twitter.android") return

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
