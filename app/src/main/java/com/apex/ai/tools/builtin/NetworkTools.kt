package com.apex.ai.tools.builtin

import com.apex.ai.llm.HttpEngine
import com.apex.ai.tools.BaseTool
import com.apex.ai.tools.ToolArg
import com.apex.ai.tools.ToolCall
import com.apex.ai.tools.ToolCategory
import com.apex.ai.tools.ToolRegistry
import com.apex.ai.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Locale

fun ToolRegistry.registerNetworkTools() {
    register(DnsTool())
    register(HttpProbeTool())
    register(PortProbeTool())
    register(UrlParserTool())
}

class DnsTool : BaseTool() {
    override val spec = ToolSpec(
        id = "dns_lookup",
        name = "DNS lookup",
        summary = "Resolve a hostname to its IPv4/IPv6 addresses and canonical name",
        category = ToolCategory.NETWORK,
        needsNetwork = true,
        args = listOf(ToolArg("host", "string", true, "Hostname, e.g. example.com"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val host = require(call, "host").trim()
        val started = System.nanoTime()
        val addresses = java.net.InetAddress.getAllByName(host)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        buildString {
            appendLine("host=$host")
            appendLine("canonical=${addresses.firstOrNull()?.canonicalHostName ?: host}")
            addresses.forEach { appendLine("address=${it.hostAddress} (${if (it is java.net.Inet6Address) "IPv6" else "IPv4"})") }
            append("resolved_in=${elapsed}ms")
        }
    }
}

class HttpProbeTool : BaseTool() {
    override val spec = ToolSpec(
        id = "http_probe",
        name = "HTTP probe",
        summary = "Fetch a URL and report status, latency, key headers and a body preview",
        category = ToolCategory.NETWORK,
        needsNetwork = true,
        args = listOf(
            ToolArg("url", "string", true, "Absolute http(s) URL"),
            ToolArg("method", "string", false, "GET | HEAD, default GET")
        )
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val url = require(call, "url").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IllegalArgumentException("URL must start with http:// or https://")
        }
        val method = (call.args["method"] ?: "GET").uppercase(Locale.US)
        val builder = Request.Builder().url(url)
        if (method == "HEAD") builder.head() else builder.get()

        val started = System.nanoTime()
        HttpEngine.client.newCall(builder.build()).execute().use { response ->
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val body = if (method == "HEAD") "" else
                (response.body?.string() ?: "").take(1200)
            buildString {
                appendLine("status=${response.code} ${response.message}")
                appendLine("latency=${elapsed}ms")
                appendLine("protocol=${response.protocol}")
                listOf("content-type", "content-length", "server", "cache-control", "strict-transport-security")
                    .forEach { name -> response.header(name)?.let { appendLine("$name: $it") } }
                if (body.isNotBlank()) {
                    appendLine("---- body preview ----")
                    append(body)
                }
            }
        }
    }
}

/**
 * TCP connect check. Deliberately scoped: a bounded port list, a short timeout
 * and a hard cap on how many sockets a single call may open. This is the same
 * primitive every network-diagnostics screen uses; it performs a connect() and
 * immediately closes, it does not send a payload.
 */
class PortProbeTool : BaseTool() {
    override val spec = ToolSpec(
        id = "port_probe",
        name = "Port reachability",
        summary = "Check whether TCP ports on a host accept connections (max 32 ports per call)",
        category = ToolCategory.NETWORK,
        needsNetwork = true,
        sensitive = true,
        args = listOf(
            ToolArg("host", "string", true, "Hostname or IP you own or administer"),
            ToolArg("ports", "string", true, "Comma-separated ports, e.g. 22,80,443,8080"),
            ToolArg("timeout_ms", "int", false, "Per-port timeout, default 900")
        )
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val host = require(call, "host").trim()
        val ports = require(call, "ports")
            .split(',', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..65535 }
            .distinct()
            .take(32)
        if (ports.isEmpty()) throw IllegalArgumentException("No valid ports supplied")
        val timeout = intArg(call, "timeout_ms", 900).coerceIn(120, 4000)

        val open = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        ports.forEach { port ->
            val reachable = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeout)
                    true
                }
            }.getOrDefault(false)
            if (reachable) open.add(port) else closed.add(port)
        }
        buildString {
            appendLine("host=$host")
            appendLine("open=${open.joinToString(",").ifEmpty { "none" }}")
            append("closed_or_filtered=${closed.joinToString(",").ifEmpty { "none" }}")
        }
    }
}

class UrlParserTool : BaseTool() {
    override val spec = ToolSpec(
        id = "url_parse",
        name = "URL parser",
        summary = "Break a URL into scheme, host, port, path and query parameters",
        category = ToolCategory.NETWORK,
        args = listOf(ToolArg("url", "string", true, "Absolute URL"))
    )

    override suspend fun execute(call: ToolCall): String {
        val raw = require(call, "url").trim()
        val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Unparseable URL") }
        val port = if (uri.port == -1) (if (uri.scheme == "https") 443 else 80) else uri.port
        return buildString {
            appendLine("scheme=${uri.scheme}")
            appendLine("host=${uri.host}")
            appendLine("port=$port")
            appendLine("path=${uri.path ?: "/"}")
            val query = uri.rawQuery
            if (!query.isNullOrBlank()) {
                appendLine("query:")
                query.split('&').forEach { pair ->
                    val parts = pair.split('=', limit = 2)
                    appendLine("  ${parts[0]} = ${parts.getOrElse(1) { "" }}")
                }
            }
            append("fragment=${uri.fragment ?: "none"}")
        }
    }
}
