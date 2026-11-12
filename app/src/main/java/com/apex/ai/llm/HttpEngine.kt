package com.apex.ai.llm

import com.apex.ai.core.ApexLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Shared SSE transport. One OkHttp client for the whole process: connection
 * pooling and HTTP/2 multiplexing are the single biggest latency win in a
 * token-streaming app, so we must not create a client per request.
 */
object HttpEngine {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)          // streaming: never time out mid-stream
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun postJson(url: String, headers: Map<String, String>, body: String): Request =
        Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): Request =
        Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

    /**
     * Executes [request] and converts the SSE body into a cold [Flow].
     * Cancelling the collector cancels the socket, which is what the Stop
     * button binds to.
     */
    fun streamSse(request: Request): Flow<String> = flow {
        val response: Response = client.newCall(request).execute()
        response.use { res ->
            if (!res.isSuccessful) {
                val errBody = runCatching { res.body?.string() }.getOrNull().orEmpty().take(600)
                ApexLog.e("HttpEngine", "HTTP ${res.code} ${res.message} :: $errBody")
                throw HttpFailure(res.code, errBody.ifBlank { res.message })
            }
            val source = res.body?.source() ?: throw HttpFailure(res.code, "Empty response body")
            var pending: String? = null
            val parser = SseParser { payload -> pending = payload }
            while (true) {
                val line = source.readUtf8Line() ?: break
                parser.feed(line)
                val ready = pending
                if (ready != null) {
                    pending = null
                    emit(ready)
                }
            }
            parser.flush()
            pending?.let { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    class HttpFailure(val code: Int, override val message: String) : Exception(message)
}
