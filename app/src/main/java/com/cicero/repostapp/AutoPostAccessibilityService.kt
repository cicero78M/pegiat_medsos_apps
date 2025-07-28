package com.cicero.repostapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cicero.repostapp.util.containsAllTexts
import com.cicero.repostapp.util.findNodesByText
import com.cicero.repostapp.util.safeClick
import com.cicero.repostapp.util.traverseParentToFindClickable
import com.cicero.repostapp.util.logNodeTree

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
        val root = rootInActiveWindow ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleShareSheet(root)
        }

        val source = event.source ?: return
        val pkg = event.packageName?.toString() ?: return
        val rule = rules.firstOrNull { it.packageName == pkg } ?: return
        if (event.eventType and serviceInfo.eventTypes == 0) return

        val now = SystemClock.elapsedRealtime()
        val last = lastExecution[pkg] ?: 0L
        if (now - last < rule.cooldownMs) return

        if (!containsAllTexts(root, rule.requiresAll, rule.maxDepth)) return

        val targets = findNodesByText(root, rule.clickTargetText, rule.maxDepth)
        if (targets.isNotEmpty() && safeClick(targets.first())) {
            log("Clicked target for $pkg")
            lastExecution[pkg] = now
        }
    }

    private fun handleShareSheet(root: AccessibilityNodeInfo) {
        val keywords = listOf("Copy link", "Salin tautan", "Twitter", "YouTube")
        val matches = keywords.flatMap { findNodesByText(root, it, 8) }
        if (matches.isEmpty()) return

        if (BuildConfig.DEBUG) logNodeTree(root)

        val target = matches.first().traverseParentToFindClickable() ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }, 300)
    }

    override fun onInterrupt() {}

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }
}

/*
 To add a new rule (e.g. Instagram or TikTok),
 add an entry with the same fields in AutoPostRuleLoader.defaultRulesJson
 or save a JSON array under SharedPreferences key "rules_json".
 */

