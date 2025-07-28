package com.cicero.repostapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

/**
 * AutoPostAccessibilityService helps automatically press the posting button on
 * supported apps.
 *
 * Enable the service through **Settings \u2192 Accessibility \u2192 Cicero
 * Reposter - AutoPost**.
 */
class AutoPostAccessibilityService : AccessibilityService() {

    private val TAG = "AutoPostService"
    private val lastExecution = mutableMapOf<String, Long>()
    private lateinit var rules: List<AutoPostRule>

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        rules = AutoPostRuleLoader.load(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val source = event.source ?: return
        val pkg = event.packageName?.toString() ?: return
        val rule = rules.firstOrNull { it.packageName == pkg } ?: return
        if (event.eventType and serviceInfo.eventTypes == 0) return

        val now = SystemClock.elapsedRealtime()
        val last = lastExecution[pkg] ?: 0L
        if (now - last < rule.cooldownMs) return

        val root = rootInActiveWindow ?: return
        if (!containsAllTexts(root, rule.requiresAll, rule.maxDepth)) return

        val targets = findNodesByText(root, rule.clickTargetText, rule.maxDepth)
        if (targets.isNotEmpty() && safeClick(targets.first())) {
            log("Clicked target for $pkg")
            lastExecution[pkg] = now
        }
    }

    override fun onInterrupt() {}

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }
}

/** Recursively search node tree for text matches. */
fun findNodesByText(root: AccessibilityNodeInfo?, text: String, maxDepth: Int = Int.MAX_VALUE): List<AccessibilityNodeInfo> {
    if (root == null) return emptyList()
    val normalized = text.normalize()
    val result = mutableListOf<AccessibilityNodeInfo>()
    fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > maxDepth) return
        val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
        if (nodeText?.normalize()?.contains(normalized) == true) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i), depth + 1)
        }
    }
    traverse(root, 0)
    return result
}

/** Check that every text is present somewhere in the tree. */
fun containsAllTexts(root: AccessibilityNodeInfo?, texts: List<String>, maxDepth: Int = Int.MAX_VALUE): Boolean {
    return texts.all { findNodesByText(root, it, maxDepth).isNotEmpty() }
}

/** Perform safe click on visible, enabled node. */
fun safeClick(node: AccessibilityNodeInfo?): Boolean {
    if (node == null || !node.isVisibleToUser || !node.isEnabled) return false
    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
}

/** Normalize string for comparison (lowercase, trim, remove diacritics). */
fun String.normalize(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("\\p{Mn}+"), "")

/*
 To add a new rule (e.g. Instagram or TikTok),
 add an entry with the same fields in AutoPostRuleLoader.defaultRulesJson
 or save a JSON array under SharedPreferences key "rules_json".
*/

