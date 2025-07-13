package com.cicero.repostapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TwitterPostService : AccessibilityService() {

    companion object {
        const val ACTION_UPLOAD_FINISHED = "com.cicero.repostapp.TWITTER_UPLOAD_FINISHED"
        var replyText: String = ""
        const val CAPTION_INPUT_ID = "com.twitter.android:id/tweet_text"
        const val TWEET_BUTTON_ID = "com.twitter.android:id/button_tweet"
    }

    private var captionInserted = false
    private var tweetClicked = false
    private var waitingUpload = false
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { performActions() }
    private val finishRunnable = Runnable { finishUpload() }
    private val stepDelayMs = 3000L
    private val uploadTimeoutMs = 15000L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf("com.twitter.android")
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (event?.packageName != "com.twitter.android") return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                captionInserted = false
                tweetClicked = false
                waitingUpload = false
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
        }
    }

    private fun performActions() {
        val root = rootInActiveWindow ?: return
        if (!captionInserted) {
            val edit = findEditText(root) ?: findNodeById(root, CAPTION_INPUT_ID)
            if (edit != null && insertCaption(edit)) {
                handler.postDelayed(clickRunnable, stepDelayMs)
                return
            }
        }

        if (captionInserted && !tweetClicked) {
            val node = findNodeById(root, TWEET_BUTTON_ID) ?: findClickableNodeByText(root, listOf("Tweet"))
            if (node != null) {
                tweetClicked = true
                waitingUpload = true
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(finishRunnable, uploadTimeoutMs)
                handler.postDelayed(clickRunnable, stepDelayMs)
                return
            }
        }

        if (waitingUpload) {
            handler.postDelayed(clickRunnable, stepDelayMs)
            return
        }
    }

    private fun findClickableNodeByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        for (t in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(t)
            for (n in nodes) {
                var current: AccessibilityNodeInfo? = n
                while (current != null && !current.isClickable) {
                    current = current.parent
                }
                if (current != null && current.isClickable) {
                    return current
                }
            }
        }
        return null
    }

    private fun findNodeById(node: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (id == node.viewIdResourceName) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findNodeById(child, id)
            if (res != null) return res
        }
        return null
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if ("android.widget.EditText" == node.className || node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditText(child)
            if (result != null) return result
        }
        return null
    }

    private fun insertCaption(node: AccessibilityNodeInfo): Boolean {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val setResult = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!setResult || node.text.isNullOrBlank()) {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        val confirmed = !node.text.isNullOrBlank()
        captionInserted = confirmed
        return confirmed
    }

    private fun finishUpload() {
        waitingUpload = false
        sendBroadcast(Intent(ACTION_UPLOAD_FINISHED))
        performGlobalAction(GLOBAL_ACTION_HOME)
        disableSelf()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(clickRunnable)
        handler.removeCallbacks(finishRunnable)
        super.onDestroy()
    }
}

