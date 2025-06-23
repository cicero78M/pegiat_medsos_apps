package com.example.repostapp

import android.os.Bundle
import android.widget.Button
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        setupPasteButton(
            R.id.text_instagram,
            R.id.button_paste_instagram,
            "Paste link Instagram"
        )
        setupPasteButton(
            R.id.text_facebook,
            R.id.button_paste_facebook,
            "Paste link Facebook"
        )
        setupPasteButton(
            R.id.text_twitter,
            R.id.button_paste_twitter,
            "Paste link Twitter"
        )
        setupPasteButton(
            R.id.text_tiktok,
            R.id.button_paste_tiktok,
            "Paste link TikTok"
        )
        setupPasteButton(
            R.id.text_youtube,
            R.id.button_paste_youtube,
            "Paste link YouTube"
        )
        findViewById<Button>(R.id.button_send_report).setOnClickListener {
            val msg = "Laporan terkirim"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPasteButton(textViewId: Int, buttonId: Int, label: String) {
        val textView = findViewById<TextView>(textViewId)
        val button = findViewById<Button>(buttonId)
        button.text = label
        button.setOnClickListener {
            if (textView.visibility == View.GONE) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val text = clip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
                if (text.isNotBlank()) {
                    textView.text = text
                    textView.visibility = View.VISIBLE
                    button.text = "Batalkan"
                }
            } else {
                textView.text = ""
                textView.visibility = View.GONE
                button.text = label
            }
        }
    }
}
