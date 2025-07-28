package com.cicero.repostapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private val reloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            rules = AutoPostRuleLoader.load(this@AutoPostAccessibilityService)
            log("Rules reloaded")
        }
    }

    companion object {
        const val ACTION_RELOAD_RULES = "com.cicero.repostapp.RELOAD_RULES"
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        registerReceiver(reloadReceiver, IntentFilter(ACTION_RELOAD_RULES))
        rules = AutoPostRuleLoader.load(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        val pkgName = event.packageName?.toString()
        log("Received event type=$eventType pkg=$pkgName - delaying 5s")

        Handler(Looper.getMainLooper()).postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed

            // Automatically click Twitter's Tweet button if visible
            val tweetButton = root
                .findAccessibilityNodeInfosByViewId("com.twitter.android:id/button_tweet")
                .firstOrNull { it.isVisibleToUser && it.isClickable }
            if (tweetButton != null) {
                tweetButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                log("Tombol Tweet diklik otomatis")
                return@postDelayed
            }

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                handleShareSheet(root)
            }

            val pkg = pkgName ?: return@postDelayed
            val rule = rules.firstOrNull { it.packageName == pkg } ?: return@postDelayed
            if (eventType and serviceInfo.eventTypes == 0) return@postDelayed

            val now = SystemClock.elapsedRealtime()
            val last = lastExecution[pkg] ?: 0L
            if (now - last < rule.cooldownMs) return@postDelayed

            if (!containsAllTexts(root, rule.requiresAll, rule.maxDepth)) return@postDelayed

            log("Checking for target text '${rule.clickTargetText}'")
            val targets = findNodesByText(root, rule.clickTargetText, rule.maxDepth)
            log(if (targets.isNotEmpty()) "Text found" else "Text not found")
            val clickable = targets.firstOrNull()?.traverseParentToFindClickable()
            if (clickable != null && safeClick(clickable)) {
                log("Clicked target for $pkg")
                lastExecution[pkg] = now
            }
        }, 5000)
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

    override fun onDestroy() {
        unregisterReceiver(reloadReceiver)
        super.onDestroy()
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }
}

/*
 To add a new rule (e.g. Instagram or TikTok),
 add an entry with the same fields in AutoPostRuleLoader.defaultRulesJson
 or save a JSON array under SharedPreferences key "rules_json".
 */

