package com.apex.ai.data.model

import java.util.UUID

enum class MessageKind { USER, ASSISTANT, TOOL, SYSTEM, PLAN }

data class StoredMessage(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = MessageKind.USER.name,
    val text: String = "",
    val agentName: String = "",
    val toolName: String = "",
    val toolOk: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class Session(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New chat",
    val agentId: String = "general",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val messages: MutableList<StoredMessage> = mutableListOf()
) {
    fun preview(): String =
        messages.lastOrNull { it.kind == MessageKind.ASSISTANT.name || it.kind == MessageKind.USER.name }
            ?.text?.take(70)?.replace('\n', ' ') ?: "Empty"
}
