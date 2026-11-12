package com.apex.ai.agents

import com.apex.ai.llm.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Public facade. Keeps the original `executeTask(prompt) -> AgentResponse` shape
 * that earlier builds of Apex used, while the streaming API is the one the UI
 * consumes. Anything already written against the old class keeps compiling.
 */
class Orchestrator(
    private val catalog: AgentCatalog,
    private val runtime: AgentRuntime,
    private val teams: TeamOrchestrator
) {

    data class AgentResponse(
        val status: String,
        val output: String,
        val errorDetected: Boolean
    )

    fun stream(agentId: String, prompt: String, history: List<ChatMessage> = emptyList()): Flow<AgentEvent> {
        val spec = catalog.byId(agentId)
        return when (spec.mode) {
            AgentMode.TEAM -> teams.run(spec, history, prompt)
            else -> runtime.run(spec, history, prompt)
        }
    }

    suspend fun executeTask(prompt: String, agentId: String = "general"): AgentResponse {
        val text = StringBuilder()
        var error: String? = null
        var steps = 0
        stream(agentId, prompt).collect { event ->
            when (event) {
                is AgentEvent.Token -> text.append(event.text)
                is AgentEvent.Completed -> steps = event.steps
                is AgentEvent.Failed -> error = event.message
                else -> Unit
            }
        }
        val failure = error
        val status = when {
            failure != null -> "FAILED"
            steps > 1 -> "SUCCESS_PATCHED"
            else -> "SUCCESS_CLEAN"
        }
        return AgentResponse(status, text.toString(), failure != null)
    }
}
