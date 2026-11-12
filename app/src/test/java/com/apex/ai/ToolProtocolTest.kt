package com.apex.ai

import com.apex.ai.tools.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolProtocolTest {

    @Test
    fun stripsToolBlocksFromVisibleText() {
        val parser = ToolCallParser()
        val visible = StringBuilder()
        visible.append(parser.feed("Let me check. "))
        visible.append(parser.feed("<tool name=\"calc\">{\"expr\":\"2+2\"}</tool>"))
        visible.append(parser.feed(" That is four."))
        visible.append(parser.flush())

        assertFalse(visible.contains("<tool"))
        assertTrue(visible.contains("Let me check."))
        assertTrue(visible.contains("That is four."))
    }

    @Test
    fun extractsToolNameAndArguments() {
        val parser = ToolCallParser()
        parser.feed("<tool name=\"hash_text\">{\"text\":\"hello\",\"algorithm\":\"md5\"}</tool>")
        val calls = parser.calls()
        assertEquals(1, calls.size)
        assertEquals("hash_text", calls[0].toolId)
        assertEquals("hello", calls[0].args["text"])
        assertEquals("md5", calls[0].args["algorithm"])
    }

    @Test
    fun holdsBackPartialTagPrefix() {
        val parser = ToolCallParser()
        val first = parser.feed("answer<to")
        assertEquals("answer", first)
        val rest = parser.feed("ol name=\"now\">{}</tool>done")
        assertFalse(rest.contains("<tool"))
        assertEquals(1, parser.calls().size)
    }

    @Test
    fun supportsMultipleCallsInOneTurn() {
        val parser = ToolCallParser()
        parser.feed("<tool name=\"now\">{}</tool><tool name=\"uuid\">{\"count\":\"2\"}</tool>")
        assertEquals(2, parser.calls().size)
    }
}
