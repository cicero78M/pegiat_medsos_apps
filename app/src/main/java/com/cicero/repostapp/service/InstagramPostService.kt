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
    }

    private var captionInserted = false
    private var shareClicked = false
    private var waitingUpload = false
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { performActions() }
    private val finishRunnable = Runnable { finishUpload() }
    private val stepDelayMs = 4000L
    private val uploadTimeoutMs = 30000L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf("com.instagram.android")
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return

        if (event?.packageName != "com.instagram.android") {
            if (containsText(root, listOf("Bagikan", "Share"))) {
                checkRememberChoice(root)
                clickInstagramFeed(root)
            }
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                captionInserted = false
                shareClicked = false
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
        }
    }

    private fun performActions() {
        val root = rootInActiveWindow ?: return

        if (containsText(root, listOf("Buat Stiker"))) {
            val laterNode = findClickableNodeByText(root, listOf("Lain Kali"))
            if (laterNode != null) {
                laterNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
            return
        }

        if (containsText(root, listOf("edit video")) && containsText(root, listOf("berikutnya"))) {
            val nextNode = findClickableNodeByText(root, listOf("Berikutnya"))
            if (nextNode != null) {
                nextNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
            return
        }

        if (!containsText(
                root,
                listOf(
                    "Tambahkan keterangan",
                    "Tulis keterangan",
                    "Tulis keterangan dan tambahkan tagar...",
                    "Add caption",
                    "Write a caption",
                    "Write a caption..."
                )
            )) {
            handler.postDelayed(clickRunnable, stepDelayMs)
            return
        }

        if (!captionInserted) {
            val editNode = waitForCaptionEditText()
            if (editNode != null) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text
                if (!text.isNullOrBlank()) {
                    val args = Bundle()
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        if (editNode.text.isNullOrBlank()) {
                            editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        }
                    } else {
                        editNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                    captionInserted = !editNode.text.isNullOrBlank()
                    handler.postDelayed(clickRunnable, stepDelayMs)
                    return
                }
            }
        }

        if (waitingUpload) {
            // wait for Instagram to finish uploading the post
            handler.postDelayed(clickRunnable, stepDelayMs)
            return
        }

        if (!shareClicked) {
            val node = findClickableNodeByText(root, listOf("Bagikan", "Share"))
            if (node != null) {
                shareClicked = true
                waitingUpload = true
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(finishRunnable, uploadTimeoutMs)
                handler.postDelayed(clickRunnable, stepDelayMs)
            }
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

    private fun containsText(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        for (k in keywords) {
            if (node.text?.toString()?.contains(k, true) == true) return true
            if (node.contentDescription?.toString()?.contains(k, true) == true) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (containsText(child, keywords)) return true
        }
        return false
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

    private fun findCaptionEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val keywords = listOf(
            "Tambahkan keterangan",
            "Tulis keterangan",
            "Tulis keterangan dan tambahkan tagar...",
            "Add caption",
            "Write a caption",
            "Write a caption..."
        )
        val target = findNodeByText(node, keywords)
        var current: AccessibilityNodeInfo? = target
        while (current != null && "android.widget.EditText" != current.className && !current.isEditable) {
            current = current.parent
        }
        return if (current != null && ("android.widget.EditText" == current.className || current.isEditable)) current else null
    }

    private fun waitForCaptionEditText(): AccessibilityNodeInfo? {
        var attempts = 0
        while (attempts < 5) {
            val root = rootInActiveWindow ?: return null
            val node = findCaptionEditText(root) ?: findEditText(root)
            if (node != null) return node
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                break
            }
            attempts++
        }
        return null
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        for (k in keywords) {
            if (node.text?.toString()?.contains(k, true) == true ||
                node.contentDescription?.toString()?.contains(k, true) == true) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findNodeByText(child, keywords)
            if (res != null) return res
        }
        return null
    }

    private fun checkRememberChoice(root: AccessibilityNodeInfo) {
        val node = findNodeByText(root, listOf("Ingat pilihan saya", "Remember my choice"))
        var target = node
        while (target != null && !target.isCheckable && !target.isClickable) {
            target = target.parent
        }
        if (target != null && !target.isChecked) {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun clickInstagramFeed(root: AccessibilityNodeInfo) {
        val node = findNodeByText(root, listOf("Instagram", "Feed")) ?: return
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun finishUpload() {
        waitingUpload = false
        sendBroadcast(Intent(ACTION_UPLOAD_FINISHED))
        performGlobalAction(GLOBAL_ACTION_HOME)
        stopSelf()
    }

    override fun onInterrupt() {}
}
