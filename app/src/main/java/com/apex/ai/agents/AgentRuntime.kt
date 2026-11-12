package com.apex.ai.agents

import com.apex.ai.core.ApexLog
import com.apex.ai.llm.ChatMessage
import com.apex.ai.llm.LlmRequest
import com.apex.ai.llm.ProviderRegistry
import com.apex.ai.llm.Role
import com.apex.ai.llm.StreamEvent
import com.apex.ai.tools.ToolCallParser
import com.apex.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The autonomous loop.
 *
 *   think → stream → collect tool calls → execute → observe → think again
 *
 * It stops on: no tool calls, a step budget, or a hard failure. The budget is the
 * safety valve that keeps a confused model from burning the user's quota.
 */
class AgentRuntime(
    private val providers: ProviderRegistry,
    private val tools: ToolRegistry,
    private val maxSteps: Int = 8,
    private val customPrompt: () -> String = { "" }
) {

    fun run(
        spec: AgentSpec,
        history: List<ChatMessage>,
        prompt: String
    ): Flow<AgentEvent> = flow {
        val provider = providers.build()
        if (provider == null) {
            emit(AgentEvent.Failed("No provider configured. Add an API key in Settings."))
            return@flow
        }
        val model = providers.activeModel()
        val systemPrompt = PromptBuilder.build(spec, tools, customPrompt())

        val messages = history.toMutableList()
        messages.add(ChatMessage(Role.USER, prompt))

        var steps = 0
        var finalText = ""

        while (steps < maxSteps) {
            steps++
            emit(AgentEvent.Status(if (steps == 1) "thinking" else "step $steps of $maxSteps"))

            val parser = ToolCallParser()
            val collected = StringBuilder()
            var failed = false
            var failureMessage = ""

            provider.stream(
                LlmRequest(
                    model = model,
                    systemPrompt = systemPrompt,
                    messages = messages.toList()
                )
            ).collect { event ->
                when (event) {
                    is StreamEvent.Delta -> {
                        collected.append(event.text)
                        val safe = parser.feed(event.text)
                        if (safe.isNotEmpty()) emit(AgentEvent.Token(safe))
                    }
                    is StreamEvent.Reasoning -> emit(AgentEvent.Reasoning(event.text))
                    is StreamEvent.Failed -> {
                        failed = true
                        failureMessage = event.message
                    }
                    is StreamEvent.Completed -> Unit
                    is StreamEvent.Usage -> Unit
                }
            }

            if (failed) {
                val tail = parser.flush()
                if (tail.isNotEmpty()) emit(AgentEvent.Token(tail))
                emit(AgentEvent.Failed(failureMessage))
                return@flow
            }

            val tail = parser.flush()
            if (tail.isNotEmpty()) emit(AgentEvent.Token(tail))

            val calls = parser.calls()
            finalText = collected.toString()

            if (calls.isEmpty()) {
                emit(AgentEvent.Completed(finalText, steps))
                return@flow
            }

            messages.add(ChatMessage(Role.ASSISTANT, collected.toString().ifBlank { "(tool call)" }))

            val observations = StringBuilder()
            calls.forEach { call ->
                val tool = tools.find(call.toolId)
                if (tool == null) {
                    emit(AgentEvent.ToolFinished(call.toolId, false, "Unknown tool", 0))
                    observations.appendLine("[${call.toolId}] ERROR: no such tool")
                    return@forEach
                }
                emit(AgentEvent.ToolStarted(call.toolId, call.args))
                val result = tools.invoke(call)
                emit(
                    AgentEvent.ToolFinished(
                        toolId = call.toolId,
                        ok = result.ok,
                        preview = result.render().take(400),
                        durationMs = result.durationMs
                    )
                )
                observations.appendLine("[${call.toolId}] ${result.render()}")
            }

            messages.add(
                ChatMessage(
                    Role.TOOL,
                    "Tool results:\n" + observations.toString().trim() +
                        "\n\nContinue. Use these results; if the task is done, answer the user directly " +
                        "with no further tool calls."
                )
            )
            ApexLog.d("Runtime", "step $steps completed with ${calls.size} tool call(s)")
        }

        emit(AgentEvent.Status("step budget reached"))
        emit(AgentEvent.Completed(finalText, steps))
    }
}
