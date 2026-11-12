package com.apex.ai.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort safety net.
 *
 * A release build that dies on launch gives the user nothing to report. This
 * writes the full stack trace plus device facts to a private file, then defers
 * to the previous handler so the platform still shows its own dialog. The next
 * successful launch surfaces the report in Settings.
 */
object ApexCrashHandler {

    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 12_000

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { persist(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun persist(app: Context, thread: Thread, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("Apex AI crash report")
            appendLine("time: $stamp")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            appendLine()
            append(writer.toString())
        }
        File(app.filesDir, FILE).writeText(Redactor.scrub(report).take(MAX_CHARS))
    }

    fun lastReport(context: Context): String? = runCatching {
        val file = File(context.applicationContext.filesDir, FILE)
        if (!file.exists()) null else file.readText().takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
