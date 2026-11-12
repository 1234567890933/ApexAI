package com.apex.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed credential store.
 *
 * Every accessor here is failure-tolerant on purpose. `EncryptedSharedPreferences`
 * can construct successfully and then throw on the first *read* when the Keystore
 * key has been invalidated — after an OS upgrade, a restore, or "clear credentials".
 * That exception used to surface on the main thread during `MainActivity.onCreate`,
 * which is why the app showed its splash and then vanished.
 *
 * Now a read failure flips [isBroken], wipes the unreadable store and returns the
 * default, so start-up always completes. The user loses stored keys once; they get
 * a working app back.
 */
class LocalVault(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
    val isHardwareBacked: Boolean

    @Volatile
    var isBroken: Boolean = false
        private set

    init {
        var store: SharedPreferences? = null
        var backed = true
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            store = EncryptedSharedPreferences.create(
                appContext,
                "apex_secure_vault",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "Keystore unavailable, degrading to private prefs", t)
            backed = false
        }
        prefs = store ?: appContext.getSharedPreferences("apex_plain_vault", Context.MODE_PRIVATE)
        isHardwareBacked = backed
    }

    // ---- safe primitives ------------------------------------------------------
    private fun readString(key: String, fallback: String?): String? {
        if (isBroken) return fallback
        return try {
            prefs.getString(key, fallback)
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "read failed for '$key' — resetting the store", t)
            resetStore()
            fallback
        }
    }

    private fun readFloat(key: String, fallback: Float): Float {
        if (isBroken) return fallback
        return try {
            prefs.getFloat(key, fallback)
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "float read failed for '$key'", t)
            resetStore()
            fallback
        }
    }

    private fun readInt(key: String, fallback: Int): Int {
        if (isBroken) return fallback
        return try {
            prefs.getInt(key, fallback)
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "int read failed for '$key'", t)
            resetStore()
            fallback
        }
    }

    private fun readBool(key: String, fallback: Boolean): Boolean {
        if (isBroken) return fallback
        return try {
            prefs.getBoolean(key, fallback)
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "bool read failed for '$key'", t)
            resetStore()
            fallback
        }
    }

    private fun write(block: (SharedPreferences.Editor) -> Unit) {
        if (isBroken) return
        try {
            val editor = prefs.edit()
            block(editor)
            editor.apply()
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "write failed", t)
            resetStore()
        }
    }

    /** Wipes the unreadable store so the next access starts clean. */
    private fun resetStore() {
        isBroken = true
        runCatching { prefs.edit().clear().commit() }
    }

    /** Used by the start-up recovery screen. */
    fun resetAll() {
        isBroken = false
        runCatching { prefs.edit().clear().commit() }
    }

    // ---- provider credentials -------------------------------------------------
    fun saveApiKey(providerId: String, apiKey: String) =
        write { it.putString(keyFor(providerId), apiKey.trim()) }

    fun getApiKey(providerId: String): String? =
        readString(keyFor(providerId), null)?.takeIf { it.isNotBlank() }

    fun clearApiKey(providerId: String) = write { it.remove(keyFor(providerId)) }

    /** Legacy single-slot accessor kept so existing installs keep working. */
    fun saveApiKey(apiKey: String) = saveApiKey(ProviderIds.DEFAULT, apiKey)

    fun getApiKey(): String? = getApiKey(ProviderIds.DEFAULT)

    // ---- non-secret preferences ----------------------------------------------
    var providerId: String
        get() = readString("provider_id", ProviderIds.DEFAULT) ?: ProviderIds.DEFAULT
        set(value) = write { it.putString("provider_id", value) }

    var modelId: String
        get() = readString("model_id", "") ?: ""
        set(value) = write { it.putString("model_id", value) }

    var baseUrl: String
        get() = readString("base_url", "") ?: ""
        set(value) = write { it.putString("base_url", value.trim()) }

    var selectedAgentId: String
        get() = readString("agent_id", "general") ?: "general"
        set(value) = write { it.putString("agent_id", value) }

    var temperature: Float
        get() = readFloat("temperature", 0.7f)
        set(value) = write { it.putFloat("temperature", value.coerceIn(0f, 2f)) }

    var voiceEnabled: Boolean
        get() = readBool("voice_enabled", false)
        set(value) = write { it.putBoolean("voice_enabled", value) }

    var hapticsEnabled: Boolean
        get() = readBool("haptics_enabled", true)
        set(value) = write { it.putBoolean("haptics_enabled", value) }

    var maxAgentSteps: Int
        get() = readInt("max_agent_steps", 8)
        set(value) = write { it.putInt("max_agent_steps", value.coerceIn(1, 24)) }

    /** Only editable while the Advanced section is unlocked. */
    var customSystemPrompt: String
        get() = readString("custom_system_prompt", "") ?: ""
        set(value) = write { it.putString("custom_system_prompt", value.trim()) }

    fun clearVault() {
        isBroken = false
        runCatching { prefs.edit().clear().apply() }
    }

    private fun keyFor(providerId: String) = "api_key_$providerId"
}

object ProviderIds {
    const val DEFAULT = "gemini"
    const val GEMINI = "gemini"
    const val OPENAI = "openai"
    const val OPENROUTER = "openrouter"
    const val GROQ = "groq"
    const val OLLAMA = "ollama"
    const val CUSTOM = "custom"
}
