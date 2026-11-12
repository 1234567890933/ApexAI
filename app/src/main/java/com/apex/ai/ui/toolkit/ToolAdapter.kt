package com.apex.ai.ui.toolkit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.tools.Tool
import com.apex.ai.tools.ToolCategory
import com.apex.ai.ui.motion.Motion

class ToolAdapter(
    private var tools: List<Tool>,
    private val onRun: (Tool) -> Unit
) : RecyclerView.Adapter<ToolAdapter.Holder>() {

    fun update(newTools: List<Tool>) {
        tools = newTools
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tools.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(tools[position], onRun)
        if (holder.itemView.tag == null) {
            holder.itemView.tag = true
            Motion.enter(holder.itemView, position)
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val glyph: TextView = view.findViewById(R.id.tvGlyph)
        private val name: TextView = view.findViewById(R.id.tvName)
        private val summary: TextView = view.findViewById(R.id.tvSummary)

        fun bind(tool: Tool, onRun: (Tool) -> Unit) {
            glyph.text = glyphFor(tool.spec.category)
            name.text = tool.spec.name
            summary.text = tool.spec.summary
            itemView.setOnClickListener {
                Motion.pulse(it)
                onRun(tool)
            }
        }

        private fun glyphFor(category: ToolCategory): String = when (category) {
            ToolCategory.COMPUTE -> "∑"
            ToolCategory.TEXT -> "❡"
            ToolCategory.NETWORK -> "◎"
            ToolCategory.SECURITY -> "⛨"
            ToolCategory.FILES -> "▤"
            ToolCategory.SYSTEM -> "⚙"
        }
    }
}
