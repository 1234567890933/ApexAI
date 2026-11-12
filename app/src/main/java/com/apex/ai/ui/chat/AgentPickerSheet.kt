package com.apex.ai.ui.chat

import android.app.Activity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apex.ai.R
import com.apex.ai.agents.AgentSpec
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Quick agent switcher. Implemented with a plain [BottomSheetDialog] rather than
 * a DialogFragment so the selection callback cannot be lost across a rotation
 * mid-dismiss.
 */
object AgentPickerSheet {

    fun show(
        activity: Activity,
        agents: List<AgentSpec>,
        selectedId: String,
        onPick: (AgentSpec) -> Unit
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Apex_BottomSheet)
        val view = activity.layoutInflater.inflate(R.layout.sheet_agents, null)
        val recycler: RecyclerView = view.findViewById(R.id.recyclerAgents)
        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = AgentAdapter(agents, selectedId) { agent ->
            onPick(agent)
            dialog.dismiss()
        }
        recycler.adapter = adapter
        dialog.setContentView(view)
        dialog.show()
    }
}
