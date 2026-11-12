package com.apex.ai.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.ui.motion.Motion

class ChatAdapter(
    private val onCopy: (String) -> Unit,
    private val onSpeak: (String) -> Unit,
    private val onRegenerate: (Int) -> Unit = {},
    private val onEditUser: (Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ChatItem>()

    fun replaceAll(newItems: List<ChatItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun add(item: ChatItem): Int {
        items.add(item)
        notifyItemInserted(items.size - 1)
        return items.size - 1
    }

    fun refresh(index: Int) {
        if (index in items.indices) notifyItemChanged(index)
    }

    fun itemAt(index: Int): ChatItem? = items.getOrNull(index)

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = items.isEmpty()

    fun lastAgentIndex(): Int = items.indexOfLast { it.kind == RowKind.AGENT }

    /** How many user bubbles exist up to and including [position]. */
    fun userOrdinalAt(position: Int): Int {
        var ordinal = 0
        val limit = position.coerceAtMost(items.size - 1)
        for (index in 0..limit) {
            if (index >= 0 && items[index].kind == RowKind.USER) ordinal++
        }
        return ordinal
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position].kind.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (RowKind.values()[viewType]) {
            RowKind.USER -> UserHolder(inflater.inflate(R.layout.item_msg_user, parent, false))
            RowKind.AGENT -> AgentHolder(inflater.inflate(R.layout.item_msg_agent, parent, false))
            else -> ToolHolder(inflater.inflate(R.layout.item_msg_tool, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is UserHolder -> holder.bind(item, position, onEditUser)
            is AgentHolder -> holder.bind(item, position, onCopy, onSpeak, onRegenerate)
            is ToolHolder -> holder.bind(item)
        }
        if (holder.itemView.tag == null) {
            holder.itemView.tag = true
            Motion.enter(holder.itemView, position)
        }
    }

    private class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.tvBody)
        fun bind(item: ChatItem, position: Int, onEditUser: (Int) -> Unit) {
            body.text = item.text
            body.setOnLongClickListener {
                onEditUser(position)
                true
            }
        }
    }

    private class AgentHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val body: TextView = view.findViewById(R.id.tvBody)
        private val name: TextView = view.findViewById(R.id.tvAgentName)
        private val avatar: TextView = view.findViewById(R.id.tvAvatar)
        private val copy: ImageButton = view.findViewById(R.id.btnCopy)
        private val speak: ImageButton = view.findViewById(R.id.btnSpeak)
        private val retry: ImageButton = view.findViewById(R.id.btnRetry)

        fun bind(
            item: ChatItem,
            position: Int,
            onCopy: (String) -> Unit,
            onSpeak: (String) -> Unit,
            onRegenerate: (Int) -> Unit
        ) {
            val context = itemView.context
            name.text = item.agentName
            avatar.text = item.glyph
            body.text = if (item.streaming) item.text + Motion.CARET else item.text
            body.setTextColor(ContextCompat.getColor(context, R.color.apex_text))
            copy.setOnClickListener { onCopy(item.text) }
            speak.setOnClickListener { onSpeak(item.text) }
            retry.setOnClickListener { onRegenerate(position) }

            val busy = item.streaming
            copy.alpha = if (busy) 0.3f else 1f
            speak.alpha = if (busy) 0.3f else 1f
            retry.alpha = if (busy) 0.3f else 1f
            copy.isEnabled = !busy
            speak.isEnabled = !busy
            retry.isEnabled = !busy
        }
    }

    private class ToolHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val glyph: TextView = view.findViewById(R.id.tvGlyph)
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val meta: TextView = view.findViewById(R.id.tvMeta)

        fun bind(item: ChatItem) {
            val context = itemView.context
            glyph.text = item.glyph
            title.text = item.title
            meta.text = item.meta
            val accent = when {
                item.kind == RowKind.STATUS -> R.color.apex_violet
                item.ok -> R.color.apex_cyan
                else -> R.color.apex_red
            }
            glyph.setTextColor(ContextCompat.getColor(context, accent))
            meta.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.ok) R.color.apex_text_faint else R.color.apex_red
                )
            )
        }
    }
}
