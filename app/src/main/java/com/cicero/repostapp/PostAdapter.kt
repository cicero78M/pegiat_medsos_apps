package com.cicero.repostapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class InstaPost(
    val id: String,
    val caption: String?,
    val imageUrl: String?,
    val createdAt: String,
    val isVideo: Boolean = false,
    val videoUrl: String? = null,
    val sourceUrl: String? = null,
    var downloaded: Boolean = false,
    var localPath: String? = null,
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
        private val linkText: TextView = itemView.findViewById(R.id.text_link)

        fun bind(post: InstaPost) {
            captionText.text = post.caption ?: ""
            linkText.text = "https://instagram.com/p/${post.id}"
        }
    }
}
