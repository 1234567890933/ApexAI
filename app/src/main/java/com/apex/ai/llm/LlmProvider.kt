package com.apex.ai.llm

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    val id: String
    val displayName: String

    /** Streams a completion. Implementations must be cancellation-safe. */
    fun stream(request: LlmRequest): Flow<StreamEvent>

    fun defaultModel(): String
}
