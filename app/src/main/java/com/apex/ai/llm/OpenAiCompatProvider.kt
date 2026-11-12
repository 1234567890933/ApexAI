package com.apex.ai.llm

import com.apex.ai.core.ApexLog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Any OpenAI-compatible chat-completions endpoint: OpenAI, OpenRouter, Groq,
 * Together, a self-hosted vLLM, LM Studio or Ollama (`/v1`). One implementation
 * covers every one of them because they all speak the same SSE dialect.
 */
class OpenAiCompatProvider(
    override val id: String,
    override val displayName: String,
    private val apiKey: String,
    private val baseUrl: String,
    private val fallbackModel: String
) : LlmProvider {

    override fun defaultModel(): String = fallbackModel

    override fun stream(request: LlmRequest): Flow<StreamEvent> = flow {
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val model = request.model.ifBlank { fallbackModel }

        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)
            addProperty("temperature", request.temperature)
            addProperty("max_tokens", request.maxTokens)
            add("messages", JsonArray().apply {
                if (request.systemPrompt.isNotBlank()) {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", request.systemPrompt)
                    })
                }
                request.messages.forEach { msg ->
                    add(JsonObject().apply {
                        addProperty("role", msg.toWireRole())
                        addProperty("content", msg.content)
                    })
                }
            })
        }

        val headers = buildMap {
            put("Content-Type", "application/json")
            if (apiKey.isNotBlank()) put("Authorization", "Bearer $apiKey")
            put("Accept", "text/event-stream")
            if (id == "openrouter") {
                put("HTTP-Referer", "https://github.com/apex-ai")
                put("X-Title", "Apex AI")
            }
        }

        val req = HttpEngine.postJson(endpoint, headers, body.toString())
        var finished = false
        try {
            HttpEngine.streamSse(req).collect { payload ->
                if (payload.trim() == "[DONE]") return@collect
                val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return@collect
                if (root.has("error")) {
                    val err = root.get("error")
                    val msg = if (err.isJsonObject) err.asJsonObject.get("message")?.asString ?: "Provider error" else err.asString
                    emit(StreamEvent.Failed(msg, false))
                    finished = true
                    return@collect
                }
                val choices = root.getAsJsonArray("choices") ?: return@collect
                if (choices.size() == 0) return@collect
                val choice = choices[0].asJsonObject
                val delta = choice.getAsJsonObject("delta")
                delta?.get("content")?.let { if (!it.isJsonNull) emit(StreamEvent.Delta(it.asString)) }
                delta?.get("reasoning_content")?.let { if (!it.isJsonNull) emit(StreamEvent.Reasoning(it.asString)) }
                choice.get("finish_reason")?.let { fr ->
                    if (!fr.isJsonNull) {
                        finished = true
                        emit(StreamEvent.Completed(fr.asString))
                    }
                }
                root.getAsJsonObject("usage")?.let { usage ->
                    emit(
                        StreamEvent.Usage(
                            usage.get("prompt_tokens")?.asInt ?: 0,
                            usage.get("completion_tokens")?.asInt ?: 0
                        )
                    )
                }
            }
            if (!finished) emit(StreamEvent.Completed("stop"))
        } catch (t: Throwable) {
            if (t is HttpEngine.HttpFailure) {
                emit(StreamEvent.Failed("HTTP ${t.code}: ${t.message.take(200)}", t.code >= 500 || t.code == 429, t.code))
            } else {
                ApexLog.e("OpenAICompat", "stream failed", t)
                emit(StreamEvent.Failed(t.message ?: "Network error", true))
            }
        }
    }
}
