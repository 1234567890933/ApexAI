package com.apex.ai.core

import android.content.Context

/**
 * Kotlin face of the Java platform layer. Keeps PackageManager out of the rest
 * of the app so there is exactly one place to audit it.
 */
object AppFacts {

    fun self(context: Context): Map<String, String> =
        com.apex.ai.platform.PackageInspector.describeSelf(context.applicationContext)

    fun pretty(context: Context): String =
        self(context).entries.joinToString("\n") { "${it.key} = ${it.value}" }
}
