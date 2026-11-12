package com.apex.ai.core

import android.content.Context
import java.security.MessageDigest

/**
 * Passcode gate for the Advanced section.
 *
 * Only a SHA-256 digest is compiled into the APK — the passphrase itself is
 * never written to the repository, so reading the source does not reveal it.
 * The gate protects configuration, not safety policy: nothing behind it
 * disables the agent's guardrails.
 */
object AdvancedGate {

    private const val PREFS = "apex_advanced"
    private const val KEY_UNLOCKED = "unlocked"
    private const val HASH = "935ff29e604d1a1d518540a02e05569ab80876d2408977879337dfa0b5ebcf08"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isUnlocked(context: Context): Boolean = prefs(context).getBoolean(KEY_UNLOCKED, false)

    fun tryUnlock(context: Context, candidate: String): Boolean {
        val digits = candidate.filter { it.isDigit() }
        if (digits.length < 8) return false
        val ok = MessageDigest.getInstance("SHA-256")
            .digest(digits.toByteArray())
            .joinToString("") { "%02x".format(it) } == HASH
        if (ok) prefs(context).edit().putBoolean(KEY_UNLOCKED, true).apply()
        ApexLog.d("AdvancedGate", if (ok) "unlocked" else "rejected attempt")
        return ok
    }

    fun lock(context: Context) {
        prefs(context).edit().putBoolean(KEY_UNLOCKED, false).apply()
    }
}
