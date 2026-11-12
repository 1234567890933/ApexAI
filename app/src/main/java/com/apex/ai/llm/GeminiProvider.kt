package com.apex.ai.llm

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.apex.ai.core.ApexLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Google Gemini via the v1beta streaming endpoint (`alt=sse`).
 * Uses the raw REST surface rather than the SDK so we control cancellation,
 * framing and token accounting ourselves.
 */
class GeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) : LlmProvider {

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"
    override fun defaultModel(): String = "gemini-2.0-flash"

    override fun stream(request: LlmRequest): Flow<StreamEvent> = flow {
        val model = request.model.ifBlank { defaultModel() }
        val url = "$baseUrl/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

        val body = JsonObject().apply {
            add("systemInstruction", JsonObject().apply {
                add("parts", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", request.systemPrompt) })
                })
            })
            add("contents", com.google.gson.JsonArray().apply {
                request.messages.forEach { msg ->
                    if (msg.role == Role.SYSTEM) return@forEach
                    add(JsonObject().apply {
                        addProperty("role", if (msg.role == Role.ASSISTANT) "model" else "user")
                        add("parts", com.google.gson.JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", msg.content) })
                        })
                    })
                }
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", request.temperature)
                addProperty("maxOutputTokens", request.maxTokens)
                if (request.jsonMode) addProperty("responseMimeType", "application/json")
            })
        }

        val req = HttpEngine.postJson(url, emptyMap(), body.toString())
        var finished = false
        try {
            HttpEngine.streamSse(req).collect { payload ->
                if (payload == "[DONE]") return@collect
                val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return@collect
                root.getAsJsonObject("promptFeedback")?.get("blockReason")?.let { reason ->
                    emit(StreamEvent.Failed("Prompt blocked by safety filter: ${reason.asString}", false))
                    finished = true
                    return@collect
                }
                val candidates = root.getAsJsonArray("candidates") ?: return@collect
                if (candidates.size() == 0) return@collect
                val candidate = candidates[0].asJsonObject
                val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
                parts?.forEach { part ->
                    val obj = part.asJsonObject
                    val text = obj.get("text")?.asString ?: return@forEach
                    if (obj.has("thought") && obj.get("thought").asBoolean) {
                        emit(StreamEvent.Reasoning(text))
                    } else {
                        emit(StreamEvent.Delta(text))
                    }
                }
                candidate.get("finishReason")?.asString?.let { reason ->
                    finished = true
                    emit(StreamEvent.Completed(reason))
                }
                root.getAsJsonObject("usageMetadata")?.let { usage ->
                    emit(
                        StreamEvent.Usage(
                            usage.get("promptTokenCount")?.asInt ?: 0,
                            usage.get("candidatesTokenCount")?.asInt ?: 0
                        )
                    )
                }
            }
            if (!finished) emit(StreamEvent.Completed("stop"))
        } catch (t: Throwable) {
            if (t is HttpEngine.HttpFailure) {
                emit(StreamEvent.Failed(humanise(t.code, t.message), t.code >= 500 || t.code == 429, t.code))
            } else {
                ApexLog.e("Gemini", "stream failed", t)
                emit(StreamEvent.Failed(t.message ?: "Network error", true))
            }
        }
    }

    private fun humanise(code: Int, body: String): String = when (code) {
        400 -> "Request rejected (400). Check the model name and message shape."
        401, 403 -> "API key rejected ($code). Re-enter it in Settings."
        404 -> "Model not found (404). Pick another model."
        429 -> "Rate limited (429). Apex will back off — try again shortly."
        in 500..599 -> "Provider unavailable ($code). Retrying usually works."
        else -> "HTTP $code: ${body.take(160)}"
    }
}
