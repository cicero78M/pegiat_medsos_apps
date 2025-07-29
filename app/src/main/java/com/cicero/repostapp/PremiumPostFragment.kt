package com.cicero.repostapp

import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.widget.TextView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.github.instagram4j.instagram4j.IGClient
import java.io.File
import com.cicero.repostapp.postTweetWithMediaResponse

class PremiumPostFragment : DashboardFragment() {
    companion object {
        fun newInstance(userId: String?, token: String?): PremiumPostFragment {
            val frag = PremiumPostFragment()
            frag.arguments = bundleOf("userId" to userId, "token" to token)
            return frag
        }
    }

    override fun showShareDialog(post: InstaPost) {
        val options = mutableListOf("Instagram", "Twitter", "TikTok")
        if (post.isVideo) {
            options.add("YouTube")
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Instagram" -> shareViaInstagram(post)
                    "Twitter" -> shareViaTwitter(post)
                    "TikTok" -> sharePost(post, "com.zhiliaoapp.musically")
                    "YouTube" -> sharePost(post, "com.google.android.youtube")
                    else -> sharePost(post)
                }
            }
            .show()
    }

    private fun shareViaInstagram(post: InstaPost) {
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressText = view.findViewById<TextView>(R.id.text_progress)
        progressText.text = "Menyiapkan..."
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val token = arguments?.getString("token") ?: ""
            val userId = arguments?.getString("userId") ?: ""

            progressText.text = "Memuat sesi..."
            val client = withContext(Dispatchers.IO) {
                InstagramShareHelper.loadClient(requireContext())
            }
            if (client == null) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Autopost Instagram belum login", Toast.LENGTH_SHORT).show()
                return@launch
            }

            progressText.text = "Memeriksa konten..."
            withContext(Dispatchers.IO) {
                InstagramShareHelper.ensureContentDownloaded(requireContext(), post)
            }

            progressText.text = "Memeriksa duplikasi..."
            val alreadyPosted = withContext(Dispatchers.IO) {
                instagramLinkExists(post.id, token, userId) || captionAlreadyExists(client, post.caption)
            }
            if (alreadyPosted) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Konten sudah dipost", Toast.LENGTH_SHORT).show()
                return@launch
            }

            progressText.text = "Mengunggah..."
            val link = withContext(Dispatchers.IO) {
                InstagramShareHelper.uploadPost(requireContext(), client, post)
            }
            dialog.dismiss()
            if (link != null) {
                withContext(Dispatchers.IO) { sendLink(post.id, link, token, userId) }
                Toast.makeText(requireContext(), "Berhasil upload", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Gagal upload ke Instagram", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareViaTwitter(post: InstaPost) {
        val view = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressText = view.findViewById<TextView>(R.id.text_progress)
        progressText.text = "Proses..."
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val token = arguments?.getString("token") ?: ""
            val userId = arguments?.getString("userId") ?: ""

            progressText.text = "Memeriksa konten..."
            val file = withContext(Dispatchers.IO) {
                InstagramShareHelper.ensureContentDownloaded(requireContext(), post)
            }
            if (file == null) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Gagal menyiapkan konten", Toast.LENGTH_SHORT).show()
                return@launch
            }

            progressText.text = "Memeriksa laporan..."
            val existingLink = withContext(Dispatchers.IO) {
                getTwitterLink(post.id, token, userId)
            }
            if (existingLink != null) {
                dialog.dismiss()
                Toast.makeText(requireContext(), "Link Twitter sudah ada", Toast.LENGTH_SHORT).show()
                return@launch
            }

            progressText.text = "Memposting..."
            val result = withContext(Dispatchers.IO) {
                postTweetWithMediaResponse(post.caption ?: "", file)
            }
            dialog.dismiss()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setMessage(result.rawResponse)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private suspend fun getTwitterLink(sc: String, token: String, userId: String): String? {
        if (token.isBlank() || userId.isBlank()) return null
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/link-reports")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()
                val arr = try { JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray() } catch (_: Exception) { JSONArray() }
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("shortcode") == sc && obj.optString("user_id") == userId) {
                        val link = obj.optString("twitter_link")
                        return link.takeIf { it.isNotBlank() }
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    private suspend fun instagramLinkExists(sc: String, token: String, userId: String): Boolean {
        if (token.isBlank() || userId.isBlank()) return false
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/link-reports")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body?.string()
                val arr = try { JSONObject(body ?: "{}").optJSONArray("data") ?: JSONArray() } catch (_: Exception) { JSONArray() }
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("shortcode") == sc && obj.optString("user_id") == userId) {
                        if (obj.optString("instagram_link").isNotBlank()) return true
                    }
                }
                false
            }
        } catch (_: Exception) { false }
    }

    private suspend fun captionAlreadyExists(client: IGClient, caption: String?): Boolean {
        if (caption.isNullOrBlank()) return false
        return try {
            val req = com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest(client.selfProfile.pk)
            val resp = client.sendRequest(req).join()
            resp.items?.any { it.caption?.text?.trim() == caption.trim() } ?: false
        } catch (_: Exception) { false }
    }

    private suspend fun sendLink(shortcode: String, link: String, token: String, userId: String) {
        if (token.isBlank() || userId.isBlank()) return
        val json = JSONObject().apply {
            put("shortcode", shortcode)
            put("user_id", userId)
            put("instagram_link", link)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/link-reports")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            client.newCall(req).execute().use { }
        } catch (_: Exception) { }
    }
}
