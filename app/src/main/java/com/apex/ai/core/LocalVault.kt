package com.apex.ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed credential store.
 *
 * Primary path: AES-256-GCM MasterKey in the Android Keystore, wrapped by
 * [EncryptedSharedPreferences] (AES-256-SIV for keys, AES-256-GCM for values).
 * Fallback path: if the Keystore rejects the key (rare OEM keystore corruption),
 * we degrade to a private-mode preference file rather than crashing the app —
 * flagged so the UI can warn the user.
 */
class LocalVault(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
    val isHardwareBacked: Boolean

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

    // ---- provider credentials -------------------------------------------------
    fun saveApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString(keyFor(providerId), apiKey.trim()).apply()
    }

    fun getApiKey(providerId: String): String? =
        prefs.getString(keyFor(providerId), null)?.takeIf { it.isNotBlank() }

    fun clearApiKey(providerId: String) {
        prefs.edit().remove(keyFor(providerId)).apply()
    }

    /** Legacy single-slot accessor kept so existing installs keep working. */
    fun saveApiKey(apiKey: String) = saveApiKey(ProviderIds.DEFAULT, apiKey)

    fun getApiKey(): String? = getApiKey(ProviderIds.DEFAULT)

    // ---- non-secret preferences ----------------------------------------------
    var providerId: String
        get() = prefs.getString("provider_id", ProviderIds.DEFAULT) ?: ProviderIds.DEFAULT
        set(value) = prefs.edit().putString("provider_id", value).apply()

    var modelId: String
        get() = prefs.getString("model_id", "") ?: ""
        set(value) = prefs.edit().putString("model_id", value).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) = prefs.edit().putString("base_url", value.trim()).apply()

    var selectedAgentId: String
        get() = prefs.getString("agent_id", "general") ?: "general"
        set(value) = prefs.edit().putString("agent_id", value).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value.coerceIn(0f, 2f)).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", false)
        set(value) = prefs.edit().putBoolean("voice_enabled", value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) = prefs.edit().putBoolean("haptics_enabled", value).apply()

    var maxAgentSteps: Int
        get() = prefs.getInt("max_agent_steps", 8)
        set(value) = prefs.edit().putInt("max_agent_steps", value.coerceIn(1, 24)).apply()

    /** Only editable while the Advanced section is unlocked. */
    var customSystemPrompt: String
        get() = prefs.getString("custom_system_prompt", "") ?: ""
        set(value) = prefs.edit().putString("custom_system_prompt", value.trim()).apply()

    fun clearVault() {
        prefs.edit().clear().apply()
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
