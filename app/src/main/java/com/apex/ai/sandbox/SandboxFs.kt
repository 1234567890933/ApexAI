package com.apex.ai.sandbox

import android.content.Context
import com.apex.ai.core.ApexLog
import java.io.File

/**
 * App-private scratch filesystem for agent file work.
 *
 * Every path is canonicalised and checked against the sandbox root before any
 * I/O, so a model that emits `../../databases/app.db` gets a clean rejection
 * rather than a data leak. Size and entry caps keep a runaway loop from filling
 * the device.
 */
class SandboxFs(context: Context) {

    val root: File = File(context.filesDir, "sandbox").apply { mkdirs() }

    private val maxReadBytes = 256 * 1024
    private val maxWriteBytes = 512 * 1024
    private val maxEntries = 400

    data class Entry(val path: String, val isDir: Boolean, val bytes: Long, val modified: Long)

    fun resolve(relative: String): File {
        val cleaned = relative.trim().trimStart('/').replace("..", "")
        val candidate = File(root, cleaned)
        val rootPath = root.canonicalPath
        val targetPath = candidate.canonicalPath
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) {
            throw SecurityException("Path escapes the sandbox: $relative")
        }
        return candidate
    }

    fun list(relative: String = ""): List<Entry> {
        val dir = resolve(relative)
        if (!dir.exists()) return emptyList()
        if (!dir.isDirectory) return listOf(entryOf(dir))
        val children = dir.listFiles()?.take(maxEntries) ?: return emptyList()
        return children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).map(::entryOf)
    }

    fun read(relative: String): String {
        val file = resolve(relative)
        if (!file.exists()) throw java.io.FileNotFoundException("No such file: $relative")
        if (file.isDirectory) throw IllegalArgumentException("$relative is a directory")
        if (file.length() > maxReadBytes) {
            throw IllegalArgumentException("File is ${file.length()} bytes; cap is $maxReadBytes")
        }
        return file.readText()
    }

    fun write(relative: String, content: String, append: Boolean = false): Long {
        val bytes = content.toByteArray().size
        if (bytes > maxWriteBytes) throw IllegalArgumentException("Payload too large ($bytes bytes)")
        val file = resolve(relative)
        file.parentFile?.mkdirs()
        if (append) file.appendText(content) else file.writeText(content)
        ApexLog.d("SandboxFs", "wrote ${file.name} ($bytes bytes)")
        return file.length()
    }

    fun delete(relative: String): Boolean {
        val file = resolve(relative)
        if (file == root) throw SecurityException("Refusing to delete the sandbox root")
        return file.deleteRecursively()
    }

    fun search(relative: String, needle: String, limit: Int = 40): List<String> {
        val dir = resolve(relative)
        val needleLower = needle.lowercase()
        val hits = mutableListOf<String>()
        dir.walkTopDown()
            .onEnter { it != root || relative.isEmpty() }
            .filter { it.isFile && it.length() <= maxReadBytes }
            .forEach { file ->
                if (hits.size >= limit) return@forEach
                runCatching {
                    file.forEachLine { line ->
                        if (hits.size < limit && line.lowercase().contains(needleLower)) {
                            val rel = file.relativeTo(root).path
                            hits.add("$rel: ${line.trim().take(160)}")
                        }
                    }
                }
            }
        return hits
    }

    private fun entryOf(file: File): Entry = Entry(
        path = file.relativeTo(root).path.ifEmpty { "." },
        isDir = file.isDirectory,
        bytes = if (file.isDirectory) 0L else file.length(),
        modified = file.lastModified()
    )
}
