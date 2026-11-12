package com.apex.ai.llm

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

data class ChatMessage(
    val role: Role,
    val content: String,
    val name: String? = null
) {
    fun toWireRole(): String = when (role) {
        Role.SYSTEM -> "system"
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
        Role.TOOL -> "user"
    }
}

data class LlmRequest(
    val model: String,
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val jsonMode: Boolean = false
)
