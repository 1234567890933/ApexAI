package com.apex.ai.tools

/**
 * Streaming filter for the tool-call protocol.
 *
 * The model is told to answer in prose and, when it needs a capability, to emit:
 *
 *     <tool name="hash_text">{"text":"hello"}</tool>
 *
 * This class consumes token deltas, releases only human-readable text, and
 * collects the tool blocks separately. It holds back a partial `"<tool"` prefix
 * so the user never sees protocol markup flash on screen — the single most
 * common polish failure in agent chat UIs.
 */
class ToolCallParser {

    private val buffer = StringBuilder()
    private var cursor = 0
    private val calls = mutableListOf<ToolCall>()
    private var sequence = 0

    // A runaway model can stream megabytes without ever closing a tag. These
    // bounds keep a bad run from growing the heap until the process dies.
    private val maxBufferChars = 96 * 1024
    private val maxCalls = 12

    private val openTag = "<tool"
    private val closeTag = "</tool>"

    /** Feeds a delta and returns the text that is safe to display right now. */
    fun feed(delta: String): String {
        if (delta.isEmpty()) return ""
        if (buffer.length > maxBufferChars) compact()
        buffer.append(delta)
        val out = StringBuilder()

        while (true) {
            val from = cursor
            val idx = buffer.indexOf(openTag, from)
            if (idx < 0) {
                // No tag opened. Release everything except a possible partial prefix.
                val tail = partialPrefixLength()
                val safeEnd = (buffer.length - tail).coerceAtLeast(cursor)
                if (safeEnd > cursor) {
                    out.append(buffer, cursor, safeEnd)
                    cursor = safeEnd
                }
                break
            }

            val end = buffer.indexOf(closeTag, idx)
            if (end < 0) {
                // Tag is still arriving. Release prose before it, then wait.
                if (idx > cursor) {
                    out.append(buffer, cursor, idx)
                    cursor = idx
                }
                break
            }

            if (idx > cursor) {
                out.append(buffer, cursor, idx)
            }
            val raw = buffer.substring(idx + openTag.length, end)
            parseBlock(raw)
            cursor = end + closeTag.length
        }
        return out.toString()
    }

    /** Releases any residual held text at end of stream. */
    fun flush(): String {
        val out = if (cursor < buffer.length) buffer.substring(cursor) else ""
        cursor = buffer.length
        return out.replace(openTag, "").replace(closeTag, "")
    }

    fun calls(): List<ToolCall> = calls.toList()

    fun clear() {
        buffer.setLength(0)
        cursor = 0
        calls.clear()
    }

    /** Drops already-emitted text so the buffer cannot grow without bound. */
    private fun compact() {
        if (cursor <= 0) return
        buffer.delete(0, cursor)
        cursor = 0
    }

    private fun partialPrefixLength(): Int {
        val max = minOf(openTag.length - 1, buffer.length)
        for (len in max downTo 1) {
            if (buffer.regionMatches(buffer.length - len, openTag, 0, len)) return len
        }
        return 0
    }

    private fun parseBlock(raw: String) {
        if (calls.size >= maxCalls) return
        // raw looks like:  name="hash_text">{"text":"abc"}
        val nameMatch = Regex("name\\s*=\\s*\"([^\"]+)\"").find(raw) ?: return
        val toolId = nameMatch.groupValues[1].trim()
        val jsonStart = raw.indexOf('>')
        val payload = if (jsonStart >= 0) raw.substring(jsonStart + 1).trim() else ""
        val args = JsonArgs.parse(payload)
        sequence += 1
        calls.add(ToolCall(id = "call_$sequence", toolId = toolId, args = args))
    }
}
