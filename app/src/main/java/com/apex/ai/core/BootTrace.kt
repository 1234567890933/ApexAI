package com.apex.ai.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Breadcrumb log for start-up.
 *
 * Written synchronously at each step, so if the process is killed rather than
 * crashing there is still a record of how far it got. The next launch reads it.
 */
object BootTrace {

    private const val CURRENT = "boot_trace.txt"
    private const val PREVIOUS = "boot_trace_prev.txt"
    private const val MAX_CHARS = 16_000

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val started = System.currentTimeMillis()

    private fun current(context: Context) = File(context.applicationContext.filesDir, CURRENT)
    private fun previous(context: Context) = File(context.applicationContext.filesDir, PREVIOUS)

    /** Rotates the last trace and starts a new one. Call once, very early. */
    fun begin(context: Context) {
        runCatching {
            val file = current(context)
            if (file.exists()) {
                previous(context).writeText(file.readText().takeLast(MAX_CHARS))
            }
            file.writeText("")
            step(context, "BOOT start")
        }
    }

    fun step(context: Context, label: String) {
        runCatching {
            val elapsed = System.currentTimeMillis() - started
            val line = "${stamp.format(Date())} +${elapsed}ms  $label\n"
            val file = current(context)
            val existing = if (file.exists()) file.readText() else ""
            file.writeText((existing + line).takeLast(MAX_CHARS))
        }
    }

    fun ok(context: Context) = step(context, "BOOT_OK first frame drawn")

    fun lastRun(context: Context): String? = runCatching {
        val file = current(context)
        if (file.exists() && file.length() > 0) file.readText() else null
    }.getOrNull()

    fun lastCompletedRun(context: Context): String? = runCatching {
        val file = previous(context)
        if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching {
            current(context).delete()
            previous(context).delete()
        }
    }
}
