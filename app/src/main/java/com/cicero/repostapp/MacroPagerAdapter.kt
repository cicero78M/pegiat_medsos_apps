package com.cicero.repostapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.Fragment

import com.cicero.repostapp.macro.Macro
import com.cicero.repostapp.macro.MacroManager

/** Adapter showing macro actions in a ViewPager. */
class MacroPagerAdapter(private val fragment: Fragment) : RecyclerView.Adapter<MacroPagerAdapter.ViewHolder>() {

    private val macro: Macro = MacroManager.load(fragment.requireContext()) ?: Macro("Default", mutableListOf())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.text.text = macro.actions.getOrNull(position)?.javaClass?.simpleName ?: "-"
    }

    override fun getItemCount(): Int = macro.actions.size

    fun addAction() {
        macro.actions.add(com.cicero.repostapp.macro.MacroAction.Click(0, 0))
        MacroManager.save(fragment.requireContext(), macro)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(android.R.id.text1)
    }
}
