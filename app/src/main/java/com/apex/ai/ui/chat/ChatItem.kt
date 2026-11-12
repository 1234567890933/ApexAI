package com.apex.ai.ui.chat

enum class RowKind { USER, AGENT, TOOL, STATUS }

fun com.apex.ai.data.model.StoredMessage.toChatItem(
    fallbackAgent: String = "Apex",
    fallbackGlyph: String = "◈"
): ChatItem = when (kind) {
    com.apex.ai.data.model.MessageKind.USER.name -> ChatItem(RowKind.USER, text)
    com.apex.ai.data.model.MessageKind.ASSISTANT.name -> ChatItem(
        kind = RowKind.AGENT,
        text = text,
        agentName = agentName.ifBlank { fallbackAgent },
        glyph = fallbackGlyph
    )
    com.apex.ai.data.model.MessageKind.TOOL.name -> ChatItem(
        kind = RowKind.TOOL,
        text = text,
        title = toolName.ifBlank { "tool" },
        ok = toolOk,
        glyph = "⌘"
    )
    else -> ChatItem(RowKind.STATUS, text, title = agentName.ifBlank { "status" }, glyph = "◇")
}

data class ChatItem(
    val kind: RowKind,
    var text: String,
    var title: String = "",
    var meta: String = "",
    var ok: Boolean = true,
    var glyph: String = "⌘",
    var agentName: String = "Apex",
    var streaming: Boolean = false
)
