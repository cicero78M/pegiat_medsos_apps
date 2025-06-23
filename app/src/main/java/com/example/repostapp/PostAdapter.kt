package com.example.repostapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class InstaPost(
    val id: String,
    val caption: String?,
    val createdAt: String
)

class PostAdapter(private val items: MutableList<InstaPost>) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun setData(data: List<InstaPost>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val captionText: TextView = itemView.findViewById(R.id.text_caption)
        private val dateText: TextView = itemView.findViewById(R.id.text_date)

        fun bind(post: InstaPost) {
            captionText.text = post.caption ?: ""
            dateText.text = post.createdAt
        }
    }
}
