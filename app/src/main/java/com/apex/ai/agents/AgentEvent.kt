package com.apex.ai.agents

/**
 * Everything the UI needs to know about a running agent. The UI is a pure
 * function of this stream: no polling, no callbacks, no shared mutable state.
 */
sealed class AgentEvent {
    data class Status(val label: String) : AgentEvent()
    data class Plan(val steps: List<String>) : AgentEvent()
    data class AgentSpawned(val name: String, val brief: String) : AgentEvent()
    data class Token(val text: String) : AgentEvent()
    data class Reasoning(val text: String) : AgentEvent()
    data class ToolStarted(val toolId: String, val args: Map<String, String>) : AgentEvent()
    data class ToolFinished(
        val toolId: String,
        val ok: Boolean,
        val preview: String,
        val durationMs: Long
    ) : AgentEvent()

    data class Completed(val text: String, val steps: Int) : AgentEvent()
    data class Failed(val message: String) : AgentEvent()
}
