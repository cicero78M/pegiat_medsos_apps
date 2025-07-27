package com.cicero.repostapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper

/**
 * AccessibilityService that presses the Posting button on the Twitter share
 * screen after the user sends a share Intent.
 */
class TwitterAutoPostService : AccessibilityService() {

    private val TAG = "TwitterAutoPostSvc"
    private val handler = Handler(Looper.getMainLooper())
    private var clickScheduled = false

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return

        val remember = findFirstByText(root, "ingat pilihan saya")
        val shareTitle = findFirstByText(root, "bagikan")
        if (remember != null && shareTitle != null && !clickScheduled) {
            val posting = findFirstByText(root, "Posting")
            if (posting != null) {
                clickScheduled = true
                Log.d(TAG, "Posting button detected, will click after delay")
                handler.postDelayed({
                    posting.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked Posting button")
                    clickScheduled = false
                }, 1000)
            }
        }
    }

    /** Recursively search the node tree for the first element matching text. */
    private fun findFirstByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.equals(text, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findFirstByText(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {}
}
