package com.cicero.repostapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class InstagramPostService : AccessibilityService() {

    companion object {
        const val ACTION_UPLOAD_FINISHED = "com.cicero.repostapp.INSTAGRAM_UPLOAD_FINISHED"
        const val CAPTION_INPUT_ID = "com.instagram.android:id/caption_input_text_view"
        const val SHARE_BUTTON_ID = "com.instagram.android:id/share_footer_button"
        const val SHARE_BUTTON_ALT_ID = "com.instagram.android:id/share_button"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stepDelayMs = 2000L
    private val uploadTimeoutMs = 30000L

    private var captionInserted = false
    private var shareClicked = false
    private var waitingUpload = false
    private var isVideoPost = false
    private var scrolledForVideo = false

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf("com.instagram.android")
            notificationTimeout = 200
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.instagram.android") return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ performActions() }, stepDelayMs)
    }

    override fun onInterrupt() {}

    private fun performActions() {
        val root = rootInActiveWindow ?: return

        // Dismiss sticker prompt
        findClickableByText(root, listOf("Buat Stiker", "Not Now", "Lain Kali"))?.run {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return handler.postDelayed({ performActions() }, stepDelayMs)
        }

        // Next for video posts
        findClickableByText(root, listOf("Berikutnya", "Selanjutnya", "Next"))?.run {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
            isVideoPost = true
            return handler.postDelayed({ performActions() }, stepDelayMs)
        }

        // Scroll video preview once
        if (isVideoPost && !scrolledForVideo) {
            scrollPreview(root)
            scrolledForVideo = true
            return handler.postDelayed({ performActions() }, stepDelayMs)
        }

        // Wait for caption field
        val captionField = findCaptionField(root) ?: return handler.postDelayed({ performActions() }, stepDelayMs)

        // Insert caption once
        if (!captionInserted) {
            captionInserted = insertCaption(captionField)
            return handler.postDelayed({ performActions() }, stepDelayMs)
        }

        // Verify caption and click share
        if (captionInserted && !shareClicked) {
            captionField.text?.let {
                if (it.isNotBlank()) {
                    findShareButton(root)?.run {
                        shareClicked = true
                        waitingUpload = true
                        performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        handler.postDelayed({ onUploadTimeout() }, uploadTimeoutMs)
                        return handler.postDelayed({ performActions() }, stepDelayMs)
                    }
                }
            }
        }
    }

    private fun findClickableByText(root: AccessibilityNodeInfo, texts: List<String>): AccessibilityNodeInfo? {
        texts.forEach { t ->
            root.findAccessibilityNodeInfosByText(t)?.forEach { node ->
                var cur: AccessibilityNodeInfo? = node
                while (cur != null && !cur.isClickable) cur = cur.parent
                if (cur?.isClickable == true) return cur
            }
        }
        return null
    }

    private fun findCaptionField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Try by view ID first
        findById(root, CAPTION_INPUT_ID)?.let { return it }
        // Otherwise find EditText
        return findEditable(root)
    }

    private fun findById(node: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName == id) return node
        for (i in 0 until node.childCount) {
            findById(node.getChild(i), id)?.let { return it }
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className == "android.widget.EditText" || node.isEditable) return node
        for (i in 0 until node.childCount) {
            findEditable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun insertCaption(node: AccessibilityNodeInfo): Boolean {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clip.primaryClip?.getItemAt(0)?.text ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        else
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (!success) node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        return !node.text.isNullOrBlank()
    }

    private fun findShareButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findById(root, SHARE_BUTTON_ID)
            ?: findById(root, SHARE_BUTTON_ALT_ID)
            ?: findClickableByText(root, listOf("Bagikan", "Share"))
    }

    private fun scrollPreview(root: AccessibilityNodeInfo) {
        findScrollable(root)?.run {
            performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            findScrollable(node.getChild(i))?.let { return it }
        }
        return null
    }

    private fun onUploadTimeout() {
        waitingUpload = false
        sendBroadcast(Intent(ACTION_UPLOAD_FINISHED))
        performGlobalAction(GLOBAL_ACTION_HOME)
        stopSelf()
    }
