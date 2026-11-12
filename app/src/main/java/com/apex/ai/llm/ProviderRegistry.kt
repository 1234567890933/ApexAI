package com.apex.ai.llm

import com.apex.ai.core.LocalVault
import com.apex.ai.core.ProviderIds

data class ProviderDescriptor(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val models: List<String>,
    val needsKey: Boolean,
    val hint: String
)

object ModelCatalog {

    val descriptors: List<ProviderDescriptor> = listOf(
        ProviderDescriptor(
            ProviderIds.GEMINI, "Google Gemini",
            "https://generativelanguage.googleapis.com",
            listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash"),
            needsKey = true,
            hint = "Free tier key from aistudio.google.com/apikey"
        ),
        ProviderDescriptor(
            ProviderIds.OPENAI, "OpenAI",
            "https://api.openai.com/v1",
            listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "o4-mini"),
            needsKey = true,
            hint = "platform.openai.com/api-keys"
        ),
        ProviderDescriptor(
            ProviderIds.OPENROUTER, "OpenRouter",
            "https://openrouter.ai/api/v1",
            listOf(
                "meta-llama/llama-3.3-70b-instruct:free",
                "deepseek/deepseek-chat",
                "qwen/qwen-2.5-72b-instruct",
                "google/gemma-2-27b-it"
            ),
            needsKey = true,
            hint = "openrouter.ai/keys — many free models"
        ),
        ProviderDescriptor(
            ProviderIds.GROQ, "Groq",
            "https://api.groq.com/openai/v1",
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
            needsKey = true,
            hint = "console.groq.com/keys — very fast inference"
        ),
        ProviderDescriptor(
            ProviderIds.OLLAMA, "Ollama (local)",
            "http://127.0.0.1:11434/v1",
            listOf("llama3.2", "qwen2.5", "phi3"),
            needsKey = false,
            hint = "Runs on-device via a LAN Ollama host"
        ),
        ProviderDescriptor(
            ProviderIds.CUSTOM, "Custom endpoint",
            "",
            listOf("custom"),
            needsKey = false,
            hint = "Any OpenAI-compatible base URL, e.g. vLLM or LM Studio"
        )
    )

    fun find(id: String): ProviderDescriptor =
        descriptors.firstOrNull { it.id == id } ?: descriptors.first()
}

class ProviderRegistry(private val vault: LocalVault) {

    fun activeDescriptor(): ProviderDescriptor = ModelCatalog.find(vault.providerId)

    fun activeModel(): String {
        val configured = vault.modelId
        if (configured.isNotBlank()) return configured
        return activeDescriptor().models.firstOrNull() ?: "gemini-2.0-flash"
    }

    /** Returns null when the configuration cannot produce a client. */
    fun build(): LlmProvider? {
        val descriptor = activeDescriptor()
        val key = vault.getApiKey(descriptor.id).orEmpty()
        if (descriptor.needsKey && key.isBlank()) return null
        return when (descriptor.id) {
            ProviderIds.GEMINI -> GeminiProvider(
                apiKey = key,
                baseUrl = vault.baseUrl.ifBlank { descriptor.defaultBaseUrl }
            )
            else -> OpenAiCompatProvider(
                id = descriptor.id,
                displayName = descriptor.label,
                apiKey = key,
                baseUrl = vault.baseUrl.ifBlank { descriptor.defaultBaseUrl },
                fallbackModel = descriptor.models.firstOrNull() ?: "gpt-4o-mini"
            )
        }
    }

    fun isConfigured(): Boolean = build() != null
}
