package com.cicero.repostapp

import android.content.Context
import com.github.instagram4j.instagram4j.IGClient
import com.github.instagram4j.instagram4j.actions.timeline.TimelineAction
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object InstagramShareHelper {
    private fun sessionFiles(context: Context): Pair<File, File> {
        val dir = context.filesDir
        return Pair(File(dir, "igclient.ser"), File(dir, "cookie.ser"))
    }

    fun loadClient(context: Context): IGClient? {
        val (clientFile, cookieFile) = sessionFiles(context)
        if (!clientFile.exists() || !cookieFile.exists()) return null
        return try {
            val client = IGClient.deserialize(clientFile, cookieFile)
            client.actions().users().info(client.selfProfile.pk).join()
            client
        } catch (_: Exception) {
            clientFile.delete()
            cookieFile.delete()
            null
        }
    }

    private fun baseDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "CiceroReposterApp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun fileForPost(context: Context, post: InstaPost): File {
        val dir = File(baseDir(context), post.id)
        if (!dir.exists()) dir.mkdirs()
        val name = post.id + if (post.isVideo) ".mp4" else ".jpg"
        return File(dir, name)
    }

    private fun coverFileForPost(context: Context, post: InstaPost): File {
        val dir = File(baseDir(context), post.id)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, post.id + ".jpg")
    }

    private fun carouselFileForPost(context: Context, post: InstaPost, index: Int): File {
        val dir = File(baseDir(context), post.id)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${post.id}_$index.jpg")
    }

    private suspend fun downloadCoverIfNeeded(context: Context, post: InstaPost): File? {
        val cover = coverFileForPost(context, post)
        if (cover.exists()) return cover
        val url = post.imageUrl ?: post.sourceUrl
        if (url.isNullOrBlank()) return null
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                cover.outputStream().use { out -> body.byteStream().copyTo(out) }
                cover
            }
        } catch (_: Exception) { null }
    }

    private suspend fun downloadIfNeeded(context: Context, post: InstaPost): File? {
        val out = fileForPost(context, post)
        if (out.exists()) return out
        val url = if (post.isVideo) post.videoUrl else post.imageUrl ?: post.sourceUrl
        if (url.isNullOrBlank()) return null
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                out.outputStream().use { outStream -> body.byteStream().copyTo(outStream) }
                if (post.isVideo) downloadCoverIfNeeded(context, post)
                out
            }
        } catch (_: Exception) { null }
    }

    private suspend fun downloadCarouselImagesIfNeeded(context: Context, post: InstaPost): List<File> {
        val files = mutableListOf<File>()
        if (!post.isCarousel || post.carouselImages.isEmpty() || post.isVideo) return files
        val client = OkHttpClient()
        val dir = carouselFileForPost(context, post, 0).parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()
        for ((idx, url) in post.carouselImages.withIndex()) {
            val f = carouselFileForPost(context, post, idx)
            if (f.exists()) { files.add(f); continue }
            if (url.isBlank()) continue
            val req = Request.Builder().url(url).build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body ?: return@use
                    f.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
            } catch (_: Exception) {}
            if (f.exists()) files.add(f)
        }
        return files
    }

    /**
     * Ensure the content for [post] has been downloaded locally. When the post
     * is a carousel this will also fetch additional images. The downloaded file
     * (image or video) is returned or `null` on failure.
     */
    suspend fun ensureContentDownloaded(context: Context, post: InstaPost): File? {
        val file = downloadIfNeeded(context, post) ?: return null
        if (!post.isVideo && post.isCarousel) {
            downloadCarouselImagesIfNeeded(context, post)
        }
        return file
    }

    suspend fun uploadPost(context: Context, client: IGClient, post: InstaPost): String? {
        val file = downloadIfNeeded(context, post) ?: return null
        if (!post.isVideo && post.isCarousel) {
            downloadCarouselImagesIfNeeded(context, post)
        }
        val result = when {
            post.isVideo -> {
                val cover = downloadCoverIfNeeded(context, post) ?: return null
                client.actions().timeline().uploadVideo(file, cover, post.caption ?: "").join()
            }
            post.isCarousel -> {
                val files = mutableListOf<File>()
                files.add(file)
                for (i in 1 until post.carouselImages.size) {
                    val f = carouselFileForPost(context, post, i)
                    if (f.exists()) files.add(f)
                }
                val infos = files.map { TimelineAction.SidecarPhoto.from(it) }
                client.actions().timeline().uploadAlbum(infos, post.caption ?: "").join()
            }
            else -> {
                client.actions().timeline().uploadPhoto(file, post.caption ?: "").join()
            }
        }
        return result.media?.code?.let { "https://instagram.com/p/$it" }
    }
}

