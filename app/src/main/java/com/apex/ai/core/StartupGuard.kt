package com.apex.ai.core

import android.content.Context
import java.io.File

/**
 * Detects "the app died before it drew anything".
 *
 * A marker file is written as the first statement of `MainActivity.onCreate` and
 * deleted once the first frame is on screen. If it is still there on the next
 * launch, the previous launch never reached its first frame — so instead of
 * repeating the same silent death, the app opens straight into a recovery screen.
 */
object StartupGuard {

    private const val MARKER = "startup_in_progress"

    private fun file(context: Context) = File(context.applicationContext.filesDir, MARKER)

    fun begin(context: Context) {
        runCatching { file(context).writeText(System.currentTimeMillis().toString()) }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun crashedLastLaunch(context: Context): Boolean =
        runCatching { file(context).exists() }.getOrDefault(false)

    fun consecutiveFailures(context: Context): Int =
        runCatching { file(context).readText().trim().toIntOrNull() ?: 0 }.getOrDefault(0)
}
