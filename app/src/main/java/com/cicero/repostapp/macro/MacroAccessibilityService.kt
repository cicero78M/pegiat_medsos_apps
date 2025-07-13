package com.cicero.repostapp.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
            is MacroAction.Repost -> {
                CoroutineScope(Dispatchers.IO).launch {
                    handleRepost(act.url, act.caption)
                    withContext(Dispatchers.Main) { executeNext() }
                }
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

    /** Download the media then share it to all supported platforms. */
    private suspend fun handleRepost(url: String, caption: String) {
        val file = withContext(Dispatchers.IO) { downloadFile(url) } ?: return
        shareToInstagram(file, caption)
        shareToTwitter(file, caption)
        shareToTikTok(file, caption)
        shareToYouTube(file, caption)
    }

    private fun downloadFile(url: String): java.io.File? {
        return try {
            val client = OkHttpClient()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val dir = java.io.File(externalCacheDir, "macro")
                if (!dir.exists()) dir.mkdirs()
                val ext = if (url.endsWith(".mp4")) ".mp4" else ".jpg"
                val file = java.io.File(dir, System.currentTimeMillis().toString() + ext)
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                file
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shareToInstagram(file: java.io.File, caption: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.instagram.android")
            type = if (file.extension == "mp4") "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun shareToTwitter(file: java.io.File, caption: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.twitter.android")
            type = if (file.extension == "mp4") "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun shareToTikTok(file: java.io.File, caption: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.zhiliaoapp.musically")
            type = if (file.extension == "mp4") "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra("android.intent.extra.TEXT", caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun shareToYouTube(file: java.io.File, caption: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.google.android.youtube")
            type = if (file.extension == "mp4") "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
