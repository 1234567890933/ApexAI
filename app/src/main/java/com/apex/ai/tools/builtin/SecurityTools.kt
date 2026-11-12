package com.apex.ai.tools.builtin

import android.content.Context
import com.apex.ai.sandbox.SandboxFs
import com.apex.ai.tools.BaseTool
import com.apex.ai.tools.ToolArg
import com.apex.ai.tools.ToolCall
import com.apex.ai.tools.ToolCategory
import com.apex.ai.tools.ToolRegistry
import com.apex.ai.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Static analysis helpers for learning reverse engineering on packages you have
 * on your own device. Everything here reads bytes that are already on disk:
 * no injection, no live process tampering, no network.
 */
fun ToolRegistry.registerSecurityTools(context: Context) {
    val sandbox = SandboxFs(context)
    register(ApkInspectorTool(sandbox))
    register(DexStringsTool(sandbox))
    register(EntropyTool(sandbox))
    register(ManifestPeekTool(sandbox))
}

private fun readBounded(file: File, cap: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    file.inputStream().use { stream ->
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < cap) {
            val read = stream.read(chunk)
            if (read <= 0) break
            out.write(chunk, 0, read)
            total += read
        }
    }
    return out.toByteArray()
}

private val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0A)
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

class ApkInspectorTool(private val sandbox: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "apk_inspect",
        name = "APK inspector",
        summary = "List the contents of an APK/AAB/JAR in the sandbox: entry count, dex files, native libs, size",
        category = ToolCategory.SECURITY,
        args = listOf(ToolArg("path", "string", true, "Archive path relative to the sandbox root"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val relative = require(call, "path")
        val file: File = sandbox.resolve(relative)
        if (!file.exists()) throw java.io.FileNotFoundException("Not found in sandbox: $relative")
        if (!file.isFile) throw IllegalArgumentException("$relative is not a file")

        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val dex = entries.filter { it.name.endsWith(".dex") }
            val libs = entries.filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
            val assets = entries.filter { it.name.startsWith("assets/") }
            val resources = entries.filter { it.name.startsWith("res/") }
            val suspicious = entries.filter {
                val n = it.name.lowercase(Locale.US)
                n.contains("root") || n.contains("busybox") || n.contains("supersu") ||
                    n.contains("magisk") || n.contains("frida")
            }
            buildString {
                appendLine("file=${file.name}")
                appendLine("size=${"%.2f".format(file.length() / 1024.0 / 1024.0)} MB")
                appendLine("entries=${entries.size}")
                appendLine("dex=${dex.size}  native_libs=${libs.size}  res=${resources.size}  assets=${assets.size}")
                if (libs.isNotEmpty()) {
                    appendLine("abis=" + libs.mapNotNull { it.name.split('/').getOrNull(1) }.distinct().joinToString())
                }
                appendLine("---- largest entries ----")
                entries.sortedByDescending { it.size }.take(12).forEach {
                    appendLine("  ${"%8d".format(it.size)}  ${it.name}")
                }
                if (suspicious.isNotEmpty()) {
                    appendLine("---- flagged names ----")
                    suspicious.take(20).forEach { appendLine("  ${it.name}") }
                } else {
                    appendLine("---- flagged names ----")
                    appendLine("  none")
                }
            }
        }
    }
}

class DexStringsTool(private val sandbox: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "dex_strings",
        name = "Strings extractor",
        summary = "Pull printable ASCII strings out of a dex/apk/binary file in the sandbox (min length 6)",
        category = ToolCategory.SECURITY,
        args = listOf(
            ToolArg("path", "string", true, "File path relative to the sandbox root"),
            ToolArg("filter", "string", false, "Only keep strings containing this substring"),
            ToolArg("limit", "int", false, "Max strings, default 120")
        )
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val relative = require(call, "path")
        val filter = call.args["filter"]?.lowercase()?.takeIf { it.isNotBlank() }
        val limit = intArg(call, "limit", 120).coerceIn(10, 500)
        val file = sandbox.resolve(relative)
        if (!file.exists()) throw java.io.FileNotFoundException("Not found in sandbox: $relative")
        if (file.length() > 40L * 1024 * 1024) throw IllegalArgumentException("File too large (40 MB cap)")

        val found = LinkedHashSet<String>()
        file.inputStream().buffered(64 * 1024).use { stream ->
            val chunk = ByteArray(64 * 1024)
            val current = StringBuilder()
            while (found.size < limit) {
                val read = stream.read(chunk)
                if (read <= 0) break
                for (i in 0 until read) {
                    val b = chunk[i].toInt() and 0xFF
                    if (b in 32..126) {
                        current.append(b.toChar())
                        if (current.length > 400) current.setLength(0)
                    } else {
                        if (current.length >= 6) {
                            val s = current.toString()
                            if (filter == null || s.lowercase().contains(filter)) found.add(s)
                        }
                        current.setLength(0)
                        if (found.size >= limit) break
                    }
                }
            }
        }
        if (found.isEmpty()) {
            "No printable strings matched."
        } else {
            buildString {
                appendLine("${found.size} string(s) extracted from $relative")
                found.forEach { appendLine(it) }
            }
        }
    }
}

class EntropyTool(private val sandbox: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "file_entropy",
        name = "Entropy & magic bytes",
        summary = "Shannon entropy plus file-type magic detection — a fast packed/encrypted payload triage",
        category = ToolCategory.SECURITY,
        args = listOf(ToolArg("path", "string", true, "File path relative to the sandbox root"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val relative = require(call, "path")
        val file = sandbox.resolve(relative)
        if (!file.exists()) throw java.io.FileNotFoundException("Not found in sandbox: $relative")
        val sample = readBounded(file, 256 * 1024)
        if (sample.isEmpty()) throw IllegalArgumentException("Empty file")

        val counts = IntArray(256)
        sample.forEach { counts[it.toInt() and 0xFF]++ }
        var entropy = 0.0
        for (count in counts) {
            if (count == 0) continue
            val p = count.toDouble() / sample.size
            entropy -= p * (Math.log(p) / Math.log(2.0))
        }
        val verdict = when {
            entropy > 7.5 -> "very high — likely compressed or encrypted"
            entropy > 6.5 -> "high — packed or compressed payload"
            entropy > 4.5 -> "moderate — typical of compiled code"
            else -> "low — plain text or structured data"
        }
        buildString {
            appendLine("file=${file.name}")
            appendLine("sampled=${sample.size} bytes")
            appendLine("entropy=${"%.4f".format(entropy)} bits/byte (max 8.0)")
            appendLine("verdict=$verdict")
            appendLine("magic=${magicOf(sample)}")
            append("sha256=" + java.security.MessageDigest.getInstance("SHA-256")
                .digest(sample).joinToString("") { "%02x".format(it) })
        }
    }

    private fun magicOf(bytes: ByteArray): String {
        fun startsWith(sig: ByteArray) =
            bytes.size >= sig.size && sig.indices.all { bytes[it] == sig[it] }
        return when {
            startsWith(ZIP_MAGIC) -> "ZIP container (APK/JAR/AAB)"
            startsWith(DEX_MAGIC) -> "Dalvik executable (dex)"
            bytes.size > 4 && bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() -> "ELF binary"
            bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "JPEG image"
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "PNG image"
            bytes.size > 4 && String(bytes, 0, 4) == "%PDF" -> "PDF document"
            else -> "unrecognised (first bytes: " +
                bytes.take(8).joinToString(" ") { "%02x".format(it) } + ")"
        }
    }
}

class ManifestPeekTool(private val sandbox: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "manifest_peek",
        name = "Manifest peek",
        summary = "Read AndroidManifest.xml out of an APK in the sandbox and report package, permissions and components",
        category = ToolCategory.SECURITY,
        args = listOf(ToolArg("path", "string", true, "APK path relative to the sandbox root"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val relative = require(call, "path")
        val file = sandbox.resolve(relative)
        if (!file.exists()) throw java.io.FileNotFoundException("Not found in sandbox: $relative")
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw IllegalArgumentException("No AndroidManifest.xml — is this an APK?")
            val bytes = zip.getInputStream(entry).use { readBounded(it, 64 * 1024) }
            // The compiled manifest is binary XML; UTF-16 string pool entries are
            // still readable as printable runs, which is enough for a quick triage.
            val strings = extractUtf16Runs(bytes)
            val packages = strings.filter { it.matches(Regex("""[a-z][a-z0-9_]*(\.[a-z0-9_]+){2,}""")) }
            val permissions = strings.filter { it.startsWith("android.permission.") }
            buildString {
                appendLine("manifest_bytes=${bytes.size}")
                appendLine("binary_xml=${!(bytes.size > 4 && bytes[0] == '<'.code.toByte())}")
                appendLine("---- candidate package / class names (${packages.size}) ----")
                packages.distinct().take(25).forEach { appendLine("  $it") }
                appendLine("---- declared permissions (${permissions.distinct().size}) ----")
                permissions.distinct().take(40).forEach { appendLine("  $it") }
            }
        }
    }

    private fun readBounded(stream: java.io.InputStream, cap: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < cap) {
            val read = stream.read(chunk)
            if (read <= 0) break
            out.write(chunk, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun extractUtf16Runs(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val builder = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            if (code in 32..126) {
                builder.append(code.toChar())
            } else {
                if (builder.length >= 5) out.add(builder.toString())
                builder.setLength(0)
            }
            i += 2
        }
        if (builder.length >= 5) out.add(builder.toString())
        return out
    }
}
