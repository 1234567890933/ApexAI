package com.apex.ai.agents

enum class AgentMode {
    /** One request, one streamed answer. Fastest path. */
    SINGLE,

    /** Autonomous loop: plan → call tools → observe → correct → answer. */
    OPEN_ENDED,

    /** Manager decomposes the goal, specialists execute, manager synthesises. */
    TEAM
}

data class TeamRole(
    val name: String,
    val brief: String
)

data class AgentSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val glyph: String,
    val systemPrompt: String,
    val toolIds: List<String> = emptyList(),
    val mode: AgentMode = AgentMode.SINGLE,
    val accentHex: String = "#7C5CFF",
    val teamRoles: List<TeamRole> = emptyList()
)
