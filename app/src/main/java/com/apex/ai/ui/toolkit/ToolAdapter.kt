package com.apex.ai.ui.toolkit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.tools.Tool
import com.apex.ai.ui.motion.Motion

/**
 * One vector icon per tool, not one per category. The icon is the fastest way
 * for a user to find a tool they have used before, so it has to be specific.
 */
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
        private val icon: ImageView = view.findViewById(R.id.ivIcon)
        private val name: TextView = view.findViewById(R.id.tvName)
        private val summary: TextView = view.findViewById(R.id.tvSummary)

        fun bind(tool: Tool, onRun: (Tool) -> Unit) {
            icon.setImageResource(iconFor(tool.spec.id))
            name.text = tool.spec.name
            summary.text = tool.spec.summary
            itemView.setOnClickListener {
                Motion.pulse(it)
                onRun(tool)
            }
        }
    }

    companion object {
        /** Explicit map: a new tool without an icon falls back to the box glyph. */
        fun iconFor(toolId: String): Int = when (toolId) {
            "calc" -> R.drawable.ic_tool_calc
            "hash_text" -> R.drawable.ic_tool_hash
            "base64" -> R.drawable.ic_tool_base64
            "uuid" -> R.drawable.ic_tool_uuid
            "now" -> R.drawable.ic_tool_now
            "json_format" -> R.drawable.ic_tool_json
            "device_info" -> R.drawable.ic_tool_device
            "text_stats" -> R.drawable.ic_tool_text
            "word_freq" -> R.drawable.ic_tool_words
            "regex_test" -> R.drawable.ic_tool_regex
            "case_convert" -> R.drawable.ic_tool_case
            "diff" -> R.drawable.ic_tool_diff
            "dns_lookup" -> R.drawable.ic_tool_globe
            "http_probe" -> R.drawable.ic_tool_http
            "port_probe" -> R.drawable.ic_tool_port
            "url_parse" -> R.drawable.ic_tool_link
            "apk_inspect" -> R.drawable.ic_tool_apk
            "manifest_peek" -> R.drawable.ic_tool_doc
            "dex_strings" -> R.drawable.ic_tool_quote
            "file_entropy" -> R.drawable.ic_tool_wave
            "fs_list" -> R.drawable.ic_tool_list
            "fs_read" -> R.drawable.ic_tool_eye
            "fs_write" -> R.drawable.ic_tool_pen
            "fs_search" -> R.drawable.ic_tool_search
            "fs_delete" -> R.drawable.ic_tool_trash
            else -> R.drawable.ic_tool_box
        }
    }
}
