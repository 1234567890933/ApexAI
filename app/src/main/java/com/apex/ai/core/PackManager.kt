package com.apex.ai.core

import android.content.Context
import com.apex.ai.llm.HttpEngine
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * One-tap engine packs.
 *
 * The APK ships code, not content. Anything bulky — the prompt library, the tool
 * guide, and whatever models come next — is downloaded once, verified against a
 * SHA-256 published in the manifest, and unpacked into app storage. That keeps
 * the download small while letting the installed app grow as far as the packs go.
 */
class PackManager(private val context: Context) {

    private val root: File = File(context.filesDir, "packs")

    data class PackInfo(val name: String, val file: String, val bytes: Long, val installed: Boolean)
    data class PromptTemplate(val category: String, val title: String, val prompt: String)

    private val baseUrl = "https://raw.githubusercontent.com/1234567890933/ApexAI/main/packs"

    fun isInstalled(): Boolean = File(root, "manifest.json").exists()

    fun installedCount(): Int =
        root.listFiles()?.count { it.isFile && it.name.endsWith(".json") } ?: 0

    fun installedSizeBytes(): Long =
        root.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun clear() {
        runCatching { root.deleteRecursively() }
    }

    fun prompts(): List<PromptTemplate> {
        val file = File(root, "prompts.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val items = JsonParser.parseString(file.readText()).asJsonObject
                .getAsJsonArray("items")
            items.mapNotNull { element ->
                val obj = element.asJsonObject
                PromptTemplate(
                    category = obj.get("category")?.asString ?: "General",
                    title = obj.get("title")?.asString ?: "",
                    prompt = obj.get("prompt")?.asString ?: ""
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun installAll(onProgress: (Float, String) -> Unit): Outcome<Int> =
        withContext(Dispatchers.IO) {
            runCatchingOutcome("E_PACK") {
                root.mkdirs()
                onProgress(0.04f, "contacting the pack server")
                val manifestBytes = fetch("$baseUrl/manifest.json")
                val manifest = JsonParser.parseString(String(manifestBytes)).asJsonObject
                val packs = manifest.getAsJsonArray("packs")
                if (packs.size() == 0) throw IllegalStateException("Manifest lists no packs")

                var installed = 0
                for (index in 0 until packs.size()) {
                    val entry = packs[index].asJsonObject
                    val file = entry.get("file").asString
                    val expected = entry.get("sha256").asString
                    onProgress(index.toFloat() / packs.size(), "downloading $file")

                    val body = fetch("$baseUrl/$file")
                    val actual = sha256(body)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw IllegalStateException("Checksum mismatch for $file — refusing to install")
                    }
                    val target = File(root, file)
                    val temp = File(root, "$file.part")
                    temp.writeBytes(body)
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        target.writeBytes(body)
                        temp.delete()
                    }
                    installed++
                }
                File(root, "manifest.json").writeBytes(manifestBytes)
                onProgress(1f, "installed $installed pack(s)")
                ApexLog.d("PackManager", "installed $installed packs, ${installedSizeBytes()} bytes")
                installed
            }
        }

    private fun fetch(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        HttpEngine.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            return response.body?.bytes() ?: throw IllegalStateException("Empty body for $url")
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
