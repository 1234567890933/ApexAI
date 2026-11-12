package com.apex.ai.tools.builtin

import android.content.Context
import com.apex.ai.sandbox.ExpressionEngine
import com.apex.ai.tools.BaseTool
import com.apex.ai.tools.ToolArg
import com.apex.ai.tools.ToolCall
import com.apex.ai.tools.ToolCategory
import com.apex.ai.tools.ToolRegistry
import com.apex.ai.tools.ToolSpec
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.text.SimpleDateFormat
import android.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

fun ToolRegistry.registerCoreTools(context: Context) {
    register(CalculatorTool())
    register(HashTool())
    register(Base64Tool())
    register(UuidTool())
    register(TimeTool())
    register(JsonFormatTool())
    register(DeviceInfoTool(context))
}

class CalculatorTool : BaseTool() {
    override val spec = ToolSpec(
        id = "calc",
        name = "Calculator",
        summary = "Evaluate an arithmetic expression exactly (+ - * / % ^ and sqrt, sin, log, min, max, avg)",
        category = ToolCategory.COMPUTE,
        args = listOf(ToolArg("expr", "string", true, "e.g. (1250*0.18)+sqrt(144)"))
    )

    override suspend fun execute(call: ToolCall): String {
        val expr = require(call, "expr")
        val value = ExpressionEngine(expr).evaluate()
        val pretty = if (value == Math.floor(value) && !value.isInfinite() && Math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.10g", value)
        }
        return "$expr = $pretty"
    }
}

class HashTool : BaseTool() {
    override val spec = ToolSpec(
        id = "hash_text",
        name = "Hasher",
        summary = "Compute a cryptographic digest of a string (md5, sha1, sha256, sha512)",
        category = ToolCategory.SECURITY,
        args = listOf(
            ToolArg("text", "string", true, "Input to hash"),
            ToolArg("algorithm", "string", false, "md5 | sha1 | sha256 | sha512, default sha256")
        )
    )

    override suspend fun execute(call: ToolCall): String {
        val text = require(call, "text")
        val algorithm = (call.args["algorithm"] ?: "sha256").uppercase(Locale.US).replace("-", "")
        val normalised = when (algorithm) {
            "MD5", "SHA1", "SHA256", "SHA512" -> algorithm
            else -> throw IllegalArgumentException("Unsupported algorithm '$algorithm'")
        }
        val digest = MessageDigest.getInstance(normalised).digest(text.toByteArray())
        return "$normalised(${text.length} chars) = ${digest.toHex()}"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

class Base64Tool : BaseTool() {
    override val spec = ToolSpec(
        id = "base64",
        name = "Base64 codec",
        summary = "Encode or decode base64 text",
        category = ToolCategory.TEXT,
        args = listOf(
            ToolArg("text", "string", true, "Payload"),
            ToolArg("mode", "string", false, "encode | decode, default encode")
        )
    )

    override suspend fun execute(call: ToolCall): String {
        val text = require(call, "text")
        return when ((call.args["mode"] ?: "encode").lowercase()) {
            "encode" -> Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
            "decode" -> String(Base64.decode(text.trim(), Base64.DEFAULT))
            else -> throw IllegalArgumentException("mode must be encode or decode")
        }
    }
}

class UuidTool : BaseTool() {
    override val spec = ToolSpec(
        id = "uuid",
        name = "UUID",
        summary = "Generate one or more random UUID v4 values",
        category = ToolCategory.COMPUTE,
        args = listOf(ToolArg("count", "int", false, "How many, default 1, max 20"))
    )

    override suspend fun execute(call: ToolCall): String {
        val count = intArg(call, "count", 1).coerceIn(1, 20)
        return (1..count).joinToString("\n") { UUID.randomUUID().toString() }
    }
}

class TimeTool : BaseTool() {
    override val spec = ToolSpec(
        id = "now",
        name = "Clock",
        summary = "Current date and time in a given timezone plus epoch millis",
        category = ToolCategory.SYSTEM,
        args = listOf(ToolArg("timezone", "string", false, "IANA zone, default Asia/Kolkata"))
    )

    override suspend fun execute(call: ToolCall): String {
        val zone = call.args["timezone"]?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata"
        val fmt = SimpleDateFormat("EEEE, dd MMMM yyyy · HH:mm:ss z", Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone(zone)
        return "${fmt.format(Date())}\nepochMillis=${System.currentTimeMillis()}"
    }
}

class JsonFormatTool : BaseTool() {
    override val spec = ToolSpec(
        id = "json_format",
        name = "JSON formatter",
        summary = "Pretty-print and validate a JSON document",
        category = ToolCategory.TEXT,
        args = listOf(ToolArg("json", "string", true, "Raw JSON"))
    )

    override suspend fun execute(call: ToolCall): String {
        val raw = require(call, "json")
        val parsed = JsonParser.parseString(raw)
        return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(parsed)
    }
}

class DeviceInfoTool(private val context: Context) : BaseTool() {
    override val spec = ToolSpec(
        id = "device_info",
        name = "Device info",
        summary = "Android version, ABI, screen metrics and available memory of this device",
        category = ToolCategory.SYSTEM
    )

    override suspend fun execute(call: ToolCall): String {
        val metrics = context.resources.displayMetrics
        val runtime = Runtime.getRuntime()
        return buildString {
            appendLine("android=${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
            appendLine("device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("abis=${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("screen=${metrics.widthPixels}x${metrics.heightPixels} @${metrics.density}dp")
            appendLine("heapMaxMb=${runtime.maxMemory() / (1024 * 1024)}")
            appendLine("heapFreeMb=${runtime.freeMemory() / (1024 * 1024)}")
            append("cores=${runtime.availableProcessors()}")
        }
    }
}
