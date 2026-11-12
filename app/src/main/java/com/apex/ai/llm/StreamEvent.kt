package com.apex.ai.llm

sealed class StreamEvent {
    /** A fragment of assistant-visible text. */
    data class Delta(val text: String) : StreamEvent()

    /** Reasoning / thinking channel where a provider exposes one. */
    data class Reasoning(val text: String) : StreamEvent()

    data class Usage(val promptTokens: Int, val completionTokens: Int) : StreamEvent()

    data class Completed(val finishReason: String?) : StreamEvent()

    data class Failed(val message: String, val retryable: Boolean, val httpCode: Int? = null) : StreamEvent()
}
