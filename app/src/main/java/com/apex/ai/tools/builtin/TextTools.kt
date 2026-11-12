package com.apex.ai.tools.builtin

import com.apex.ai.tools.BaseTool
import com.apex.ai.tools.ToolArg
import com.apex.ai.tools.ToolCall
import com.apex.ai.tools.ToolCategory
import com.apex.ai.tools.ToolRegistry
import com.apex.ai.tools.ToolSpec
import java.util.Locale

fun ToolRegistry.registerTextTools() {
    register(TextStatsTool())
    register(WordFrequencyTool())
    register(RegexTool())
    register(CaseTool())
    register(DiffTool())
}

class TextStatsTool : BaseTool() {
    override val spec = ToolSpec(
        id = "text_stats",
        name = "Text statistics",
        summary = "Characters, words, lines, sentences and estimated reading time",
        category = ToolCategory.TEXT,
        args = listOf(ToolArg("text", "string", true, "Input text"))
    )

    override suspend fun execute(call: ToolCall): String {
        val text = require(call, "text")
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val sentences = text.split(Regex("[.!?]+")).count { it.isNotBlank() }
        val readingSeconds = (words.size / 3.5).toInt()
        return buildString {
            appendLine("characters=${text.length}")
            appendLine("characters_no_space=${text.count { !it.isWhitespace() }}")
            appendLine("words=${words.size}")
            appendLine("lines=${text.lines().size}")
            appendLine("sentences=$sentences")
            append("reading_time=${readingSeconds}s")
        }
    }
}

class WordFrequencyTool : BaseTool() {
    private val stop = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "has", "are", "was",
        "were", "will", "would", "you", "your", "but", "not", "can", "all", "any", "our",
        "its", "it's", "into", "than", "then", "they", "them", "there", "here", "been"
    )

    override val spec = ToolSpec(
        id = "word_freq",
        name = "Word frequency",
        summary = "Most frequent meaningful words in a text block",
        category = ToolCategory.TEXT,
        args = listOf(
            ToolArg("text", "string", true, "Input text"),
            ToolArg("top", "int", false, "How many results, default 12")
        )
    )

    override suspend fun execute(call: ToolCall): String {
        val text = require(call, "text")
        val top = intArg(call, "top", 12).coerceIn(1, 50)
        val counts = text.lowercase(Locale.US)
            .split(Regex("[^a-z0-9']+"))
            .filter { it.length > 2 && it !in stop }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(top)
        if (counts.isEmpty()) return "No meaningful tokens found."
        return counts.joinToString("\n") { "${it.key} × ${it.value}" }
    }
}

class RegexTool : BaseTool() {
    override val spec = ToolSpec(
        id = "regex_test",
        name = "Regex tester",
        summary = "Run a Java regular expression against text and list matches with groups",
        category = ToolCategory.TEXT,
        args = listOf(
            ToolArg("pattern", "string", true, "Java regex"),
            ToolArg("text", "string", true, "Haystack")
        )
    )

    override suspend fun execute(call: ToolCall): String {
        val pattern = require(call, "pattern")
        val text = require(call, "text")
        val regex = runCatching { Regex(pattern) }
            .getOrElse { throw IllegalArgumentException("Invalid pattern: ${it.message}") }
        val matches = regex.findAll(text).take(50).toList()
        if (matches.isEmpty()) return "0 matches"
        return buildString {
            appendLine("${matches.size} match(es)")
            matches.forEachIndexed { index, match ->
                appendLine("[${index + 1}] ${match.value}")
                match.groupValues.drop(1).forEachIndexed { groupIndex, group ->
                    if (group.isNotEmpty()) appendLine("    group ${groupIndex + 1} = $group")
                }
            }
        }.trim()
    }
}

class CaseTool : BaseTool() {
    override val spec = ToolSpec(
        id = "case_convert",
        name = "Case converter",
        summary = "Convert text to snake_case, kebab-case, camelCase, PascalCase, UPPER or lower",
        category = ToolCategory.TEXT,
        args = listOf(
            ToolArg("text", "string", true, "Input"),
            ToolArg("target", "string", true, "snake | kebab | camel | pascal | upper | lower | title")
        )
    )

    override suspend fun execute(call: ToolCall): String {
        val text = require(call, "text")
        val target = require(call, "target").lowercase()
        val words = text.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
        return when (target) {
            "snake" -> words.joinToString("_") { it.lowercase() }
            "kebab" -> words.joinToString("-") { it.lowercase() }
            "camel" -> words.mapIndexed { i, w ->
                if (i == 0) w.lowercase() else w.lowercase().replaceFirstChar { it.uppercase() }
            }.joinToString("")
            "pascal" -> words.joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            "upper" -> text.uppercase(Locale.US)
            "lower" -> text.lowercase(Locale.US)
            "title" -> words.joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            else -> throw IllegalArgumentException("Unknown target '$target'")
        }
    }
}

class DiffTool : BaseTool() {
    override val spec = ToolSpec(
        id = "diff",
        name = "Line diff",
        summary = "Line-level diff between two texts (added, removed, common counts)",
        category = ToolCategory.TEXT,
        args = listOf(
            ToolArg("a", "string", true, "Original"),
            ToolArg("b", "string", true, "Modified")
        )
    )

    override suspend fun execute(call: ToolCall): String {
        val a = require(call, "a").lines()
        val b = require(call, "b").lines()
        val setA = a.toSet()
        val setB = b.toSet()
        val removed = a.filterNot { it in setB }
        val added = b.filterNot { it in setA }
        return buildString {
            appendLine("common=${a.count { it in setB }}")
            appendLine("removed=${removed.size} added=${added.size}")
            removed.take(20).forEach { appendLine("- $it") }
            added.take(20).forEach { appendLine("+ $it") }
        }.trim()
    }
}
