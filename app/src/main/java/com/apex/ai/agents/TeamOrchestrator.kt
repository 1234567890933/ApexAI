package com.apex.ai.agents

import com.apex.ai.core.ApexLog
import com.apex.ai.llm.ChatMessage
import com.apex.ai.llm.LlmRequest
import com.apex.ai.llm.ProviderRegistry
import com.apex.ai.llm.Role
import com.apex.ai.llm.StreamEvent
import com.apex.ai.tools.ToolRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Manager / worker topology.
 *
 * The manager writes a JSON plan, each specialist runs the full agent loop with
 * the manager's brief, then the manager synthesises one answer. Sequential by
 * design: parallel worker streams would triple peak token throughput and, on a
 * phone, that means rate limits and thermal throttling rather than speed.
 */
class TeamOrchestrator(
    private val providers: ProviderRegistry,
    private val tools: ToolRegistry,
    private val maxSteps: Int = 8,
    private val customPrompt: () -> String = { "" }
) {

    private val runtime = AgentRuntime(providers, tools, maxSteps, customPrompt)

    private data class Assignment(val role: String, val task: String)

    fun run(team: AgentSpec, history: List<ChatMessage>, prompt: String): Flow<AgentEvent> = flow {
        val provider = providers.build()
        if (provider == null) {
            emit(AgentEvent.Failed("No provider configured. Add an API key in Settings."))
            return@flow
        }

        emit(AgentEvent.Status("manager · drafting the plan"))
        val plan = runCatching {
            requestPlan(team, prompt)
        }.getOrElse {
            ApexLog.w("Team", "plan request failed, using default roles: ${it.message}")
            team.teamRoles.map { role -> Assignment(role.name, "${role.brief} Goal: $prompt") }
        }

        if (plan.isEmpty()) {
            emit(AgentEvent.Failed("The manager produced no plan. Try rephrasing the goal."))
            return@flow
        }

        emit(AgentEvent.Plan(plan.map { "${it.role}: ${it.task}" }))

        val findings = StringBuilder()
        plan.forEachIndexed { index, assignment ->
            emit(AgentEvent.AgentSpawned(assignment.role, assignment.task))
            emit(AgentEvent.Status("${assignment.role} · ${index + 1} of ${plan.size}"))

            val worker = AgentSpec(
                id = "${team.id}-${assignment.role.lowercase()}",
                name = assignment.role,
                tagline = assignment.task,
                glyph = team.glyph,
                systemPrompt = buildString {
                    appendLine("You are ${assignment.role}, a specialist on a team led by a manager.")
                    appendLine("Your brief: ${assignment.task}")
                    appendLine("Do only your part. Be concrete and brief; your output is input for the manager,")
                    appendLine("not the final answer. Use tools where they make your part verifiable.")
                },
                toolIds = team.toolIds,
                mode = AgentMode.OPEN_ENDED,
                accentHex = team.accentHex
            )

            val workerHistory = listOf(ChatMessage(Role.USER, "Overall goal: $prompt"))
            val answer = StringBuilder()
            runtime.run(worker, workerHistory, assignment.task).collect { event ->
                when (event) {
                    is AgentEvent.Token -> answer.append(event.text)
                    is AgentEvent.ToolStarted -> emit(AgentEvent.ToolStarted(event.toolId, event.args))
                    is AgentEvent.ToolFinished -> emit(event)
                    is AgentEvent.Reasoning -> Unit
                    is AgentEvent.Failed -> {
                        answer.append("[${assignment.role} failed: ${event.message}]")
                    }
                    else -> Unit
                }
            }
            findings.appendLine("### ${assignment.role}")
            findings.appendLine(answer.toString().trim().ifBlank { "(no output)" })
            findings.appendLine()
        }

        emit(AgentEvent.Status("manager · synthesising"))
        val synthesis = StringBuilder()
        val systemPrompt = PromptBuilder.synthesisPrompt(team)
        val messages = listOf(
            ChatMessage(Role.USER, "Goal: $prompt"),
            ChatMessage(Role.USER, "Specialist findings:\n\n" + findings.toString().trim())
        )

        provider.stream(
            LlmRequest(
                model = providers.activeModel(),
                systemPrompt = systemPrompt,
                messages = messages
            )
        ).collect { event ->
            when (event) {
                is StreamEvent.Delta -> {
                    synthesis.append(event.text)
                    emit(AgentEvent.Token(event.text))
                }
                is StreamEvent.Failed -> emit(AgentEvent.Failed(event.message))
                else -> Unit
            }
        }

        emit(AgentEvent.Completed(synthesis.toString(), plan.size + 1))
    }

    private suspend fun requestPlan(team: AgentSpec, prompt: String): List<Assignment> {
        val provider = providers.build() ?: return emptyList()
        val raw = StringBuilder()
        provider.stream(
            LlmRequest(
                model = providers.activeModel(),
                systemPrompt = PromptBuilder.managerPlanPrompt(team),
                messages = listOf(ChatMessage(Role.USER, prompt)),
                temperature = 0.3f
            )
        ).collect { event ->
            when (event) {
                is StreamEvent.Delta -> raw.append(event.text)
                is StreamEvent.Failed -> throw IllegalStateException(event.message)
                else -> Unit
            }
        }
        return parsePlan(raw.toString(), team)
    }

    private fun parsePlan(raw: String, team: AgentSpec): List<Assignment> {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val json = cleaned.substring(start, end + 1)
        val array = runCatching { JsonParser.parseString(json).asJsonArray }.getOrNull() ?: return emptyList()
        val allowed = team.teamRoles.map { it.name }
        val assignments = mutableListOf<Assignment>()
        array.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val obj = element.asJsonObject
            val role = obj.get("role")?.asString?.takeIf { it.isNotBlank() } ?: return@forEach
            val task = obj.get("task")?.asString?.takeIf { it.isNotBlank() } ?: return@forEach
            val normalised = allowed.firstOrNull { it.equals(role, ignoreCase = true) } ?: role
            assignments.add(Assignment(normalised, task))
        }
        return assignments.take(4)
    }
}
