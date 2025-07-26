package com.cicero.repostapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fragment with a ViewPager that triggers Twitter autopost when button clicked.
 */
class TwitterAutopostFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_twitter_autopost, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pager = view.findViewById<ViewPager2>(R.id.twitterViewPager)
        pager.adapter = AutoPostPagerAdapter()
    }

    /** Adapter for ViewPager items. */
    inner class AutoPostPagerAdapter : RecyclerView.Adapter<AutoPostPagerAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_twitter_autopost, parent, false)
            return VH(v)
        }
        override fun getItemCount() = 1
        override fun onBindViewHolder(holder: VH, position: Int) {}
        inner class VH(val v: View) : RecyclerView.ViewHolder(v) {
            init {
                val btn = v.findViewById<Button>(R.id.btnPostToTwitter)
                btn.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) { performTwitterPost() }
                }
            }
        }
    }

    /** Data representation of Instagram post. */
    data class InstaPost(val id: String, val caption: String, val imageUrl: String, val timestamp: Long)

    /** Fetch latest post from official Instagram page. */
    private fun fetchLatestPost(username: String): InstaPost? {
        val url = "https://www.instagram.com/$username/?__a=1&__d=dis"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = JSONObject(body)
                val node = obj.optJSONObject("graphql")
                    ?.optJSONObject("user")
                    ?.optJSONObject("edge_owner_to_timeline_media")
                    ?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")
                    ?: return null
                val ts = node.optLong("taken_at_timestamp")
                val caption = node.optJSONObject("edge_media_to_caption")
                    ?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")
                    ?.optString("text") ?: ""
                val img = node.optString("display_url")
                val id = node.optString("id")
                InstaPost(id, caption, img, ts)
            }
        } catch (_: Exception) { null }
    }

    /** Ensure the post image is downloaded and return the file path. */
    private fun ensureDownloaded(post: InstaPost): File? {
        val dir = File(requireContext().getExternalFilesDir(null), "OfficialPosts")
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, post.id + ".jpg")
        if (out.exists()) return out
        val client = OkHttpClient()
        val req = Request.Builder().url(post.imageUrl).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.byteStream()?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                out
            }
        } catch (_: Exception) { null }
    }

    private suspend fun performTwitterPost() {
        val post = fetchLatestPost("instagram") ?: return
        val postDate = Instant.ofEpochSecond(post.timestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        if (postDate != LocalDate.now()) return // no post today
        val file = ensureDownloaded(post) ?: return
        val prefs = requireContext().getSharedPreferences("twitter_post_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("twitter_post_text", post.caption)
            .putString("twitter_post_image", file.absolutePath).apply()
        withContext(Dispatchers.Main) {
            val intent = requireContext().packageManager.getLaunchIntentForPackage("com.twitter.android")
            if (intent != null) startActivity(intent) else {
                android.widget.Toast.makeText(requireContext(), "Twitter not installed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
