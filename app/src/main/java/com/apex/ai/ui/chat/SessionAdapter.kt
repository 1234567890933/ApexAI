package com.apex.ai.ui.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.data.model.Session
import com.apex.ai.ui.motion.Motion

class SessionAdapter(
    private var sessions: List<Session> = emptyList(),
    private val onOpen: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.Holder>() {

    fun update(newSessions: List<Session>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sessions.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(sessions[position], onOpen)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val preview: TextView = view.findViewById(R.id.tvPreview)

        fun bind(session: Session, onOpen: (Session) -> Unit) {
            val context = itemView.context
            title.text = session.title.ifBlank { "Untitled" }
            val when_ = DateUtils.getRelativeTimeSpanString(
                session.updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            preview.text = "${session.messages.size} msgs · $when_ · ${session.agentId}"
            itemView.setOnClickListener {
                Motion.pulse(it)
                onOpen(session)
            }
            itemView.alpha = 0.9f
            title.setTextColor(
                androidx.core.content.ContextCompat.getColor(context, R.color.apex_text)
            )
        }
    }
}
