package com.apex.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credential store that never touches the Android Keystore on the main thread.
 *
 * Creating the Keystore master key is the single slowest call in this app — it
 * can take a second on a healthy phone and can stall for many seconds on a device
 * whose keystore is wedged. It used to run inside `Application.onCreate`, which
 * meant the launcher showed the splash and then killed the process for being
 * unresponsive: no crash dialog, no first frame, nothing to report.
 *
 * Now the store is built on a background thread via [warmUp]. Until it is ready,
 * reads return their default and writes are queued, so the UI can come up
 * immediately and nothing is lost.
 */
class LocalVault(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    var isHardwareBacked: Boolean = true
        private set

    @Volatile
    var isBroken: Boolean = false
        private set

    @Volatile
    var isReady: Boolean = false
        private set

    private val pendingWrites = ArrayList<(SharedPreferences.Editor) -> Unit>(16)
    private val lock = Any()

    /** Call from a worker thread. Idempotent and safe to call twice. */
    fun warmUp() {
        if (isReady) return
        synchronized(lock) {
            if (isReady) return
            val store = try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    "apex_secure_vault",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                ApexLog.e("LocalVault", "Keystore unavailable, degrading to private prefs", t)
                isHardwareBacked = false
                null
            } ?: appContext.getSharedPreferences("apex_plain_vault", Context.MODE_PRIVATE)

            prefs = store
            isReady = true
            flushPending(store)
        }
    }

    private fun flushPending(store: SharedPreferences) {
        val queued = synchronized(lock) {
            val copy = pendingWrites.toList()
            pendingWrites.clear()
            copy
        }
        queued.forEach { block ->
            runCatching { store.edit().also(block).apply() }
        }
    }

    // ---- safe primitives ------------------------------------------------------
    private fun readString(key: String, fallback: String?): String? {
        val store = prefs ?: return fallback
        if (isBroken) return fallback
        return try {
            store.getString(key, fallback)
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "read failed for '$key' — resetting the store", t)
            resetStore()
            fallback
        }
    }

    private fun readFloat(key: String, fallback: Float): Float {
        val store = prefs ?: return fallback
        if (isBroken) return fallback
        return try {
            store.getFloat(key, fallback)
        } catch (t: Throwable) {
            resetStore(); fallback
        }
    }

    private fun readInt(key: String, fallback: Int): Int {
        val store = prefs ?: return fallback
        if (isBroken) return fallback
        return try {
            store.getInt(key, fallback)
        } catch (t: Throwable) {
            resetStore(); fallback
        }
    }

    private fun readBool(key: String, fallback: Boolean): Boolean {
        val store = prefs ?: return fallback
        if (isBroken) return fallback
        return try {
            store.getBoolean(key, fallback)
        } catch (t: Throwable) {
            resetStore(); fallback
        }
    }

    private fun write(block: (SharedPreferences.Editor) -> Unit) {
        if (isBroken) return
        val store = prefs
        if (store == null) {
            // Store is still warming up: queue it, never block the caller.
            synchronized(lock) {
                if (pendingWrites.size < 64) pendingWrites.add(block)
            }
            return
        }
        try {
            store.edit().also(block).apply()
        } catch (t: Throwable) {
            ApexLog.e("LocalVault", "write failed", t)
            resetStore()
        }
    }

    private fun resetStore() {
        isBroken = true
        runCatching { prefs?.edit()?.clear()?.commit() }
    }

    /** Used by the start-up recovery screen. */
    fun resetAll() {
        isBroken = false
        runCatching { prefs?.edit()?.clear()?.commit() }
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
        runCatching { prefs?.edit()?.clear()?.apply() }
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
