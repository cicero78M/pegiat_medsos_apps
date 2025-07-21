package com.cicero.repostapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.recyclerview.widget.RecyclerView

data class InstaPost(
    val id: String,
    val caption: String?,
    val imageUrl: String?,
    val createdAt: String,
    /**
     * Sequential task number as fetched from the API queue.
     */
    val taskNumber: Int = 0,
    val isVideo: Boolean = false,
    val videoUrl: String? = null,
    val sourceUrl: String? = null,
    /**
     * Whether this post contains multiple images (carousel).
     */
    val isCarousel: Boolean = false,
    /**
     * Additional image URLs when the post is a carousel.
     */
    val carouselImages: List<String> = emptyList(),
    var downloaded: Boolean = false,
    var localPath: String? = null,
    /**
     * Paths for downloaded carousel images.
     */
    var localCarouselPaths: MutableList<String> = mutableListOf(),
    /**
     * Directory containing downloaded carousel images.
     */
    var localCarouselDir: String? = null,
    var reported: Boolean = false
)

class PostAdapter(
    private val items: MutableList<InstaPost>,
    private val onItemClicked: (InstaPost) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClicked(item) }
    }

    fun setData(data: List<InstaPost>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val captionText: TextView = itemView.findViewById(R.id.text_caption)
        private val imageView: ImageView = itemView.findViewById(R.id.image_post)
        private val downloadedIcon: ImageView = itemView.findViewById(R.id.icon_downloaded)
        private val reportedIcon: ImageView = itemView.findViewById(R.id.icon_reported)

        fun bind(post: InstaPost) {
            val cap = post.caption ?: ""
            captionText.text = "Laporan Tugas ${post.taskNumber}\n$cap"
            val url = post.imageUrl ?: post.carouselImages.firstOrNull()
            if (url != null && url.isNotBlank()) {
                Glide.with(itemView).load(url).into(imageView)
            } else {
                imageView.setImageDrawable(null)
            }
            downloadedIcon.visibility = if (post.downloaded) View.VISIBLE else View.GONE
            reportedIcon.visibility = if (post.reported) View.VISIBLE else View.GONE
        }
    }
}
