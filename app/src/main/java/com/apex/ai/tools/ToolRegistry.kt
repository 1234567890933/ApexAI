package com.apex.ai.tools

import com.apex.ai.core.ApexLog
import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.spec.id] = tool
    }

    fun size(): Int = tools.size

    fun all(): List<Tool> = tools.values.sortedBy { it.spec.id }

    fun find(id: String): Tool? = tools[id]

    fun byCategory(category: ToolCategory): List<Tool> = all().filter { it.spec.category == category }

    fun subset(ids: List<String>): List<Tool> {
        if (ids.isEmpty()) return emptyList()
        val wanted = ids.toSet()
        return all().filter { it.spec.id in wanted }
    }

    /**
     * Compact manifest injected into the system prompt. Kept terse on purpose:
     * every extra token here is paid for on every single turn.
     */
    fun manifest(ids: List<String>): String {
        val selected = if (ids.isEmpty()) all() else subset(ids)
        if (selected.isEmpty()) return "(no tools available)"
        return selected.joinToString("\n") { tool ->
            val s = tool.spec
            val args = if (s.args.isEmpty()) "" else
                " args{" + s.args.joinToString(", ") { "${it.name}:${it.type}${if (it.required) "" else "?"}" } + "}"
            "- ${s.id}: ${s.summary}$args"
        }
    }

    suspend fun invoke(call: ToolCall): ToolResult {
        val tool = tools[call.toolId]
            ?: return ToolResult(call.toolId, false, "", 0, "Unknown tool '${call.toolId}'")
        ApexLog.d("Tool", "invoke ${call.toolId} ${call.args.keys}")
        val result = tool.invoke(call)
        ApexLog.d("Tool", "${call.toolId} ok=${result.ok} ${result.durationMs}ms")
        return result
    }
}
