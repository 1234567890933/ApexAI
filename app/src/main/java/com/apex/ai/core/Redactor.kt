package com.apex.ai.core

/**
 * Strips credentials out of anything that leaves the engine: logs, transcripts,
 * crash notes and tool output rendered on screen.
 */
object Redactor {

    private val patterns = listOf(
        Regex("""(?i)(api[_-]?key|apikey|token|secret|password|authorization)\s*[:=]\s*["']?([A-Za-z0-9_\-\.]{6,})"""),
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""sk-[A-Za-z0-9_\-]{16,}"""),
        Regex("""gsk_[A-Za-z0-9_\-]{16,}"""),
        Regex("""(?i)bearer\s+[A-Za-z0-9_\-\.=]{10,}""")
    )

    private const val MASK = "***redacted***"

    fun scrub(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var out: String = input ?: return ""
        out = patterns[0].replace(out) { m ->
            val label = m.groupValues[1]
            "$label=$MASK"
        }
        for (i in 1 until patterns.size) {
            out = patterns[i].replace(out, MASK)
        }
        return out
    }

    fun fingerprint(secret: String?): String {
        if (secret.isNullOrBlank()) return "—"
        return if (secret.length <= 6) "••••" else "••••" + secret.takeLast(4)
    }
}
