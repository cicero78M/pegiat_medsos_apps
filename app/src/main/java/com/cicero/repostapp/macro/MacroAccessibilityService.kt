package com.cicero.repostapp.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** Accessibility service executing saved macros when triggered. */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_RUN = "com.cicero.repostapp.RUN_MACRO"
    }

    private var running = false
    private var actions: MutableList<MacroAction> = mutableListOf()

    override fun onServiceConnected() {
        // nothing special
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Start when receiving broadcast
    }

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RUN && !running) {
            MacroManager.load(this)?.let {
                actions = it.actions
                running = true
                executeNext()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun executeNext() {
        if (actions.isEmpty()) {
            running = false
            return
        }
        val act = actions.removeAt(0)
        when (act) {
            is MacroAction.Click -> performGesture(createTap(act.x, act.y)) { executeNext() }
            is MacroAction.Swipe -> performGesture(createSwipe(act)) { executeNext() }
            is MacroAction.SetText -> {
                // not implemented: requires focused field
                executeNext()
            }
        }
    }

    private fun performGesture(desc: GestureDescription, callback: () -> Unit) {
        dispatchGesture(desc, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback()
            }
        }, null)
    }

    private fun createTap(x: Int, y: Int): GestureDescription {
        val p = Path()
        p.moveTo(x.toFloat(), y.toFloat())
        val s = GestureDescription.StrokeDescription(p, 0, 100)
        return GestureDescription.Builder().addStroke(s).build()
    }

    private fun createSwipe(a: MacroAction.Swipe): GestureDescription {
        val p = Path()
        p.moveTo(a.startX.toFloat(), a.startY.toFloat())
        p.lineTo(a.endX.toFloat(), a.endY.toFloat())
        val s = GestureDescription.StrokeDescription(p, 0, 300)
        return GestureDescription.Builder().addStroke(s).build()
    }
}
