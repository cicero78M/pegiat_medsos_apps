package com.cicero.repostapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShareChooserService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (containsText(root, listOf("Bagikan", "Share"))) {
            checkRememberChoice(root)
            clickInstagramFeed(root)
        }
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

    override fun onInterrupt() {}
}

