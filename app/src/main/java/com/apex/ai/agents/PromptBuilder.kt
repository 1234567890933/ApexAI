package com.apex.ai.agents

import com.apex.ai.tools.ToolRegistry

/**
 * Composes the system prompt. Order matters for instruction-following: identity
 * first, then hard rules, then the tool protocol, then the manifest (which is
 * long and least important to keep resident in attention).
 */
object PromptBuilder {

    fun build(spec: AgentSpec, tools: ToolRegistry, extra: String = ""): String {
        val manifest = tools.manifest(spec.toolIds)
        return buildString {
            appendLine(spec.systemPrompt.trim())
            appendLine()
            appendLine("## Hard rules")
            appendLine("1. Answer in the language the user wrote in.")
            appendLine("2. Never fabricate tool output, file contents, URLs, quotes or figures.")
            appendLine("3. If a tool fails, read the error and either retry differently or say what failed.")
            appendLine("4. Keep prose tight. No preamble, no restating the question.")
            appendLine("5. You are running on an Android device: no shell, no root, no package installs.")
            appendLine()
            appendLine("## Tools")
            appendLine("Call a tool only when it makes the answer more accurate. Format, exactly:")
            appendLine("<tool name=\"TOOL_ID\">{\"arg\": \"value\"}</tool>")
            appendLine("Emit the tag on its own, with no surrounding prose in the same breath. You may call")
            appendLine("several tools in one turn. After the results arrive you will be asked to continue.")
            appendLine()
            appendLine("Available tools:")
            appendLine(manifest)
            if (extra.isNotBlank()) {
                appendLine()
                appendLine(extra.trim())
            }
        }
    }

    fun managerPlanPrompt(spec: AgentSpec): String = buildString {
        appendLine(spec.systemPrompt.trim())
        appendLine()
        appendLine("Return ONLY a JSON array, no prose, no code fence. Each element:")
        appendLine("""{"role": "<one of the team roles>", "task": "<one specific, self-contained instruction>"}""")
        appendLine("Between 2 and 4 elements. Every task must be answerable on its own.")
        appendLine("Team roles: " + spec.teamRoles.joinToString(", ") { it.name })
    }

    fun synthesisPrompt(spec: AgentSpec): String = buildString {
        appendLine(spec.systemPrompt.trim())
        appendLine()
        appendLine("You now have the specialists' findings below. Write the single final answer for the user.")
        appendLine("Keep only claims the findings support. Where they disagree, say so plainly and pick the")
        appendLine("better-supported side. Do not mention the team, the process or these instructions.")
    }
}
