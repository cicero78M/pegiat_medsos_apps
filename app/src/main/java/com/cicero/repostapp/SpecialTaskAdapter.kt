package com.cicero.repostapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Simple adapter to show special task recap per user.
 */
class SpecialTaskAdapter(
    private val items: MutableList<SpecialTask>
) : RecyclerView.Adapter<SpecialTaskAdapter.TaskViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_special_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun setData(data: List<SpecialTask>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.text_name)
        private val countText: TextView = itemView.findViewById(R.id.text_count)
        fun bind(task: SpecialTask) {
            nameText.text = task.displayName
            countText.text = "${task.linkCount} link"
        }
    }
}

/**
 * Data class representing special task recap for a user.
 */
data class SpecialTask(
    val displayName: String,
    val linkCount: Int
)
