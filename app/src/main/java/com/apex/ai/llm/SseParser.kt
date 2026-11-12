package com.apex.ai.llm

/**
 * Server-Sent Events reader.
 *
 * The wire format is line oriented: `field: value`, events separated by a blank
 * line, `:` starts a comment. We only need `data` payloads, and we tolerate
 * multi-line data by joining on newline as the spec requires.
 */
class SseParser(private val onPayload: (String) -> Unit) {

    private val data = StringBuilder()

    fun feed(line: String) {
        when {
            line.isEmpty() -> flush()
            line.startsWith(":") -> Unit                       // heartbeat / comment
            line.startsWith("data:") -> {
                val chunk = line.removePrefix("data:").let { if (it.startsWith(" ")) it.substring(1) else it }
                if (data.isNotEmpty()) data.append('\n')
                data.append(chunk)
            }
            line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:") -> Unit
            else -> {
                // Some gateways omit the `data:` prefix entirely.
                if (data.isNotEmpty()) data.append('\n')
                data.append(line)
            }
        }
    }

    fun flush() {
        if (data.isEmpty()) return
        val payload = data.toString()
        data.setLength(0)
        if (payload.isNotBlank()) onPayload(payload)
    }
}
