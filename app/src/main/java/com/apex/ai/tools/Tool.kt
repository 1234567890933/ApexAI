package com.apex.ai.tools

data class ToolArg(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String
)

data class ToolSpec(
    val id: String,
    val name: String,
    val summary: String,
    val category: ToolCategory,
    val args: List<ToolArg> = emptyList(),
    val needsNetwork: Boolean = false,
    /** Destructive or privacy-sensitive: the runtime asks the user first. */
    val sensitive: Boolean = false
) {
    fun signature(): String =
        if (args.isEmpty()) "$id()"
        else "$id(" + args.joinToString(", ") { "${it.name}: ${it.type}" } + ")"
}

enum class ToolCategory { COMPUTE, TEXT, NETWORK, SECURITY, FILES, SYSTEM }

data class ToolCall(
    val id: String,
    val toolId: String,
    val args: Map<String, String>
)

data class ToolResult(
    val toolId: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long = 0L,
    val error: String? = null
) {
    fun render(): String = if (ok) output else "ERROR: ${error ?: "failed"}"
}

interface Tool {
    val spec: ToolSpec
    suspend fun invoke(call: ToolCall): ToolResult
}

/** Convenience base class that handles argument validation and timing. */
abstract class BaseTool : Tool {

    protected fun arg(call: ToolCall, name: String): String? =
        call.args[name]?.takeIf { it.isNotBlank() }

    protected fun require(call: ToolCall, name: String): String =
        arg(call, name) ?: throw IllegalArgumentException("Missing required argument '$name'")

    protected fun intArg(call: ToolCall, name: String, fallback: Int): Int =
        call.args[name]?.trim()?.toIntOrNull() ?: fallback

    override suspend fun invoke(call: ToolCall): ToolResult {
        val started = System.nanoTime()
        return try {
            val text = execute(call)
            ToolResult(
                toolId = spec.id,
                ok = true,
                output = text,
                durationMs = (System.nanoTime() - started) / 1_000_000
            )
        } catch (t: Throwable) {
            ToolResult(
                toolId = spec.id,
                ok = false,
                output = "",
                durationMs = (System.nanoTime() - started) / 1_000_000,
                error = t.message ?: t.javaClass.simpleName
            )
        }
    }

    protected abstract suspend fun execute(call: ToolCall): String
}
