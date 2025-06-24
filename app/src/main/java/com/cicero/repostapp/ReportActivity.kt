package com.cicero.repostapp

import android.os.Bundle
import android.widget.Button
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URL = "image_url"
        const val EXTRA_CAPTION = "caption"
    }

    private data class Platform(
        val name: String,
        val textId: Int,
        val buttonId: Int,
        val label: String,
        val placeholder: String
    )

    private lateinit var platforms: List<Platform>
    private lateinit var token: String
    private lateinit var userId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.mipmap.ic_launcher_round)
        supportActionBar?.setDisplayUseLogoEnabled(true)

        val prefs = getSharedPreferences("auth", MODE_PRIVATE)
        token = prefs.getString("token", "") ?: ""
        userId = prefs.getString("userId", "") ?: ""

        // Show preview if provided
        intent.getStringExtra(EXTRA_IMAGE_URL)?.let { url ->
            val imageView = findViewById<ImageView>(R.id.image_preview)
            Glide.with(this).load(url).into(imageView)
        }
        intent.getStringExtra(EXTRA_CAPTION)?.let { caption ->
            val captionView = findViewById<TextView>(R.id.caption_preview)
            captionView.text = caption
        }

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
            sendReport()
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

    private fun extractShortcode(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val segments = uri.pathSegments
            val idx = segments.indexOfFirst {
                it == "p" || it == "reel" || it == "reels" || it == "tv"
            }
            if (idx >= 0 && segments.size > idx + 1) segments[idx + 1] else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun isDuplicate(link: String): Boolean {
        if (token.isBlank()) return false
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://papiqo.com/api/link-reports")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body?.string()
                val arr = try {
                    JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray()
                } catch (_: Exception) { JSONArray() }
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val links = listOf(
                        obj.optString("instagram_link"),
                        obj.optString("facebook_link"),
                        obj.optString("twitter_link"),
                        obj.optString("tiktok_link"),
                        obj.optString("youtube_link")
                    )
                    if (links.any { it.equals(link, true) }) return true
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resetField(platform: Platform) {
        val textView = findViewById<TextView>(platform.textId)
        val button = findViewById<Button>(platform.buttonId)
        textView.text = platform.placeholder
        button.text = platform.label
    }

    private fun sendReport() {
        val links = mutableMapOf<String, String?>()
        platforms.forEach { p ->
            val text = findViewById<TextView>(p.textId).text.toString().trim()
            links[p.name] = if (text.startsWith("http")) text else null
        }

        if (links["instagram"].isNullOrBlank() ||
            links["facebook"].isNullOrBlank() ||
            links["twitter"].isNullOrBlank()
        ) {
            Toast.makeText(this, "Lengkapi link Instagram, Facebook, dan Twitter", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var valid = true
            for (p in platforms) {
                val link = links[p.name]
                if (!link.isNullOrBlank() && isDuplicate(link)) {
                    withContext(Dispatchers.Main) {
                        resetField(p)
                        Toast.makeText(this@ReportActivity, "Link ${p.name} sudah ada", Toast.LENGTH_SHORT).show()
                    }
                    links[p.name] = null
                }
            }

            if (links["instagram"] == null || links["facebook"] == null || links["twitter"] == null) {
                valid = false
            }

            if (valid) {
                val shortcode = extractShortcode(links["instagram"]!!)
                val json = JSONObject().apply {
                    put("shortcode", shortcode ?: "")
                    put("user_id", userId)
                    put("instagram_link", links["instagram"])
                    put("facebook_link", links["facebook"])
                    put("twitter_link", links["twitter"])
                    put("tiktok_link", links["tiktok"])
                    put("youtube_link", links["youtube"])
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val client = OkHttpClient()
                val req = Request.Builder()
                    .url("https://papiqo.com/api/link-reports")
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
                val success = try {
                    client.newCall(req).execute().use { it.isSuccessful }
                } catch (_: Exception) { false }
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@ReportActivity, "Laporan terkirim", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ReportActivity, "Gagal mengirim laporan", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReportActivity, "Periksa kembali link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
