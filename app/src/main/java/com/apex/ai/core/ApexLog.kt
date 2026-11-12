package com.apex.ai.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer. The Settings screen renders this so a user can see what
 * the engine actually did without hooking up logcat. Bounded so it can never
 * grow without limit during a long autonomous run.
 */
object ApexLog {

    private const val CAPACITY = 400
    private val buffer = ArrayDeque<String>(CAPACITY)
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val listeners = mutableListOf<(String) -> Unit>()

    @Synchronized
    fun d(tag: String, message: String) {
        push("D", tag, message)
    }

    @Synchronized
    fun w(tag: String, message: String) {
        push("W", tag, message)
    }

    @Synchronized
    fun e(tag: String, message: String, t: Throwable? = null) {
        push("E", tag, message + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun push(level: String, tag: String, message: String) {
        val line = "${stamp.format(Date())} $level/$tag  ${Redactor.scrub(message)}"
        buffer.addLast(line)
        while (buffer.size > CAPACITY) buffer.removeFirst()
        listeners.toList().forEach { runCatching { it(line) } }
    }

    @Synchronized
    fun snapshot(): List<String> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    fun observe(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun unobserve(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
