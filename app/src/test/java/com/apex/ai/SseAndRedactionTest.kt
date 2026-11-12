package com.apex.ai

import com.apex.ai.core.Redactor
import com.apex.ai.llm.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseAndRedactionTest {

    @Test
    fun parsesSingleEvent() {
        val payloads = mutableListOf<String>()
        val parser = SseParser { payloads.add(it) }
        parser.feed("data: {\"a\":1}")
        parser.feed("")
        assertEquals(listOf("{\"a\":1}"), payloads)
    }

    @Test
    fun ignoresCommentsAndEventNames() {
        val payloads = mutableListOf<String>()
        val parser = SseParser { payloads.add(it) }
        parser.feed(": keep-alive")
        parser.feed("event: message")
        parser.feed("data: hello")
        parser.feed("")
        assertEquals(listOf("hello"), payloads)
    }

    @Test
    fun joinsMultiLineData() {
        val payloads = mutableListOf<String>()
        val parser = SseParser { payloads.add(it) }
        parser.feed("data: line1")
        parser.feed("data: line2")
        parser.feed("")
        assertEquals(listOf("line1\nline2"), payloads)
    }

    @Test
    fun redactsSecrets() {
        val scrubbed = Redactor.scrub("api_key=sk-abcdef1234567890abcdef")
        assertTrue(scrubbed.contains("redacted"))
        assertFalse(scrubbed.contains("abcdef1234567890"))
    }

    @Test
    fun fingerprintsWithoutLeaking() {
        assertEquals("••••abcd", Redactor.fingerprint("secretvalueabcd"))
        assertEquals("—", Redactor.fingerprint(null))
    }
}
