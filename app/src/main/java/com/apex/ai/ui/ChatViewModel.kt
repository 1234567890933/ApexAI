package com.apex.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apex.ai.agents.AgentCatalog
import com.apex.ai.agents.AgentEvent
import com.apex.ai.agents.AgentRuntime
import com.apex.ai.agents.Orchestrator
import com.apex.ai.agents.TeamOrchestrator
import com.apex.ai.core.AdvancedGate
import com.apex.ai.core.AppContainer
import com.apex.ai.data.model.MessageKind
import com.apex.ai.data.model.Session
import com.apex.ai.data.model.StoredMessage
import com.apex.ai.llm.ChatMessage
import com.apex.ai.llm.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Owns conversation state and the engine wiring. The activity only renders and
 * forwards taps, so a configuration change never loses a run.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    val catalog: AgentCatalog = container.agents

    var session: Session = Session(agentId = container.vault.selectedAgentId)
        private set

    fun newSession(agentId: String) {
        persist()
        container.vault.selectedAgentId = agentId
        session = Session(agentId = agentId)
    }

    /**
     * Builds the orchestrator per turn so a settings change (model, temperature,
     * step budget) takes effect immediately instead of after a restart.
     */
    private fun orchestrator(): Orchestrator {
        val steps = container.vault.maxAgentSteps
        val extra = if (AdvancedGate.isUnlocked(getApplication())) {
            container.vault.customSystemPrompt
        } else {
            ""
        }
        return Orchestrator(
            catalog = catalog,
            runtime = AgentRuntime(container.providers, container.tools, steps, customPrompt = { extra }),
            teams = TeamOrchestrator(container.providers, container.tools, steps, customPrompt = { extra })
        )
    }

    fun send(agentId: String, prompt: String): Flow<AgentEvent> {
        val history = session.messages
            .filter { it.kind == MessageKind.USER.name || it.kind == MessageKind.ASSISTANT.name }
            .takeLast(12)
            .map { stored ->
                ChatMessage(
                    role = if (stored.kind == MessageKind.USER.name) Role.USER else Role.ASSISTANT,
                    content = stored.text
                )
            }
        if (session.messages.isEmpty()) {
            session.title = prompt.take(42).replace('\n', ' ')
        }
        session.messages.add(StoredMessage(kind = MessageKind.USER.name, text = prompt))
        return orchestrator().stream(agentId, prompt, history)
    }

    fun recordAssistant(agentName: String, text: String) {
        if (text.isBlank()) return
        session.messages.add(
            StoredMessage(kind = MessageKind.ASSISTANT.name, text = text, agentName = agentName)
        )
    }

    fun persist() {
        session.updatedAt = System.currentTimeMillis()
        val snapshot = session
        viewModelScope.launch(Dispatchers.IO) { container.sessions.upsert(snapshot) }
    }

    fun recentSessions(): List<Session> = container.sessions.loadAll()
}
