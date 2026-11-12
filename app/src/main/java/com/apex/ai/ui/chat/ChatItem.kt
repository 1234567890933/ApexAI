package com.apex.ai.ui.chat

enum class RowKind { USER, AGENT, TOOL, STATUS }

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
