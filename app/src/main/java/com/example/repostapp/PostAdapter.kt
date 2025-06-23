package com.example.repostapp

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
    val isVideo: Boolean = false,
    val videoUrl: String? = null,
    val sourceUrl: String? = null,
    var downloaded: Boolean = false
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

        fun bind(post: InstaPost) {
            captionText.text = post.caption ?: ""
            val url = post.imageUrl
            if (url != null && url.isNotBlank()) {
                Glide.with(itemView).load(url).into(imageView)
            } else {
                imageView.setImageDrawable(null)
            }
            downloadedIcon.visibility = if (post.downloaded) View.VISIBLE else View.GONE
        }
    }
}
