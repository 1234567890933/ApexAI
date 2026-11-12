package com.apex.ai.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.agents.AgentMode
import com.apex.ai.agents.AgentSpec
import com.apex.ai.ui.motion.Motion

class AgentAdapter(
    private var agents: List<AgentSpec>,
    private var selectedId: String,
    private val onPick: (AgentSpec) -> Unit
) : RecyclerView.Adapter<AgentAdapter.Holder>() {

    fun update(newAgents: List<AgentSpec>, newSelectedId: String) {
        agents = newAgents
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = agents.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_agent, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(agents[position], agents[position].id == selectedId, onPick)
        if (holder.itemView.tag == null) {
            holder.itemView.tag = true
            Motion.enter(holder.itemView, position)
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val glyph: TextView = view.findViewById(R.id.tvGlyph)
        private val name: TextView = view.findViewById(R.id.tvName)
        private val tagline: TextView = view.findViewById(R.id.tvTagline)
        private val mode: TextView = view.findViewById(R.id.tvMode)

        fun bind(agent: AgentSpec, selected: Boolean, onPick: (AgentSpec) -> Unit) {
            val context = itemView.context
            glyph.text = agent.glyph
            name.text = agent.name
            tagline.text = agent.tagline
            mode.text = when (agent.mode) {
                AgentMode.SINGLE -> "single"
                AgentMode.OPEN_ENDED -> "autonomous"
                AgentMode.TEAM -> "team"
            }
            val tint = when (agent.mode) {
                AgentMode.SINGLE -> R.color.apex_text_dim
                AgentMode.OPEN_ENDED -> R.color.apex_cyan
                AgentMode.TEAM -> R.color.apex_pink
            }
            mode.setTextColor(ContextCompat.getColor(context, tint))
            itemView.alpha = if (selected) 1f else 0.72f
            itemView.setOnClickListener {
                Motion.pulse(it)
                onPick(agent)
            }
        }
    }
}
