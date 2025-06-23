package com.example.repostapp

import android.os.Bundle
import android.widget.Button
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity

class ReportActivity : AppCompatActivity() {

    private data class Platform(
        val name: String,
        val textId: Int,
        val buttonId: Int,
        val label: String,
        val placeholder: String
    )

    private lateinit var platforms: List<Platform>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher)
        supportActionBar?.setDisplayUseLogoEnabled(true)

        platforms = listOf(
            Platform(
                "instagram",
                R.id.text_instagram,
                R.id.button_paste_instagram,
                "Paste link Instagram",
                "https://instagram.com/placeholder"
            ),
            Platform(
                "facebook",
                R.id.text_facebook,
                R.id.button_paste_facebook,
                "Paste link Facebook",
                "https://facebook.com/placeholder"
            ),
            Platform(
                "twitter",
                R.id.text_twitter,
                R.id.button_paste_twitter,
                "Paste link Twitter",
                "https://twitter.com/placeholder"
            ),
            Platform(
                "tiktok",
                R.id.text_tiktok,
                R.id.button_paste_tiktok,
                "Paste link TikTok",
                "https://www.tiktok.com/placeholder"
            ),
            Platform(
                "youtube",
                R.id.text_youtube,
                R.id.button_paste_youtube,
                "Paste link YouTube",
                "https://youtu.be/placeholder"
            )
        )

        platforms.forEach { setupPasteButton(it) }

        autoPasteFromClipboard()
        findViewById<Button>(R.id.button_send_report).setOnClickListener {
            val msg = "Laporan terkirim"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPasteButton(platform: Platform) {
        val textView = findViewById<TextView>(platform.textId)
        val button = findViewById<Button>(platform.buttonId)
        textView.text = platform.placeholder
        button.text = platform.label
        button.setOnClickListener {
            if (button.text != "Batalkan") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val text = clip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
                if (isValidLink(text, platform.name)) {
                    textView.text = text.trim()
                    button.text = "Batalkan"
                } else {
                    Toast.makeText(this, "Link konten tidak valid", Toast.LENGTH_SHORT).show()
                }
            } else {
                textView.text = platform.placeholder
                button.text = platform.label
            }
        }
    }

    private fun isValidLink(link: String, platform: String): Boolean {
        if (link.isBlank()) return false
        val uri = try { Uri.parse(link.trim()) } catch (_: Exception) { return false }
        val host = uri.host ?: return false
        return when (platform) {
            "instagram" -> host.contains("instagram.com")
            "facebook" -> host.contains("facebook.com") || host.contains("fb.watch") || host.contains("fb.com")
            "twitter" -> host.contains("twitter.com") || host.contains("x.com")
            "tiktok" -> host.contains("tiktok.com")
            "youtube" -> host.contains("youtube.com") || host.contains("youtu.be")
            else -> false
        }
    }

    private fun detectPlatform(link: String): Platform? {
        return platforms.firstOrNull { isValidLink(link, it.name) }
    }

    private fun autoPasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        val platform = detectPlatform(text)
        if (platform != null) {
            val textView = findViewById<TextView>(platform.textId)
            val button = findViewById<Button>(platform.buttonId)
            if (button.text != "Batalkan") {
                textView.text = text.trim()
                button.text = "Batalkan"
            }
        }
    }
}
