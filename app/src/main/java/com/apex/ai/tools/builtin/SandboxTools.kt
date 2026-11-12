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

fun ToolRegistry.registerSandboxTools(context: Context) {
    val fs = SandboxFs(context)
    register(FsListTool(fs))
    register(FsReadTool(fs))
    register(FsWriteTool(fs))
    register(FsSearchTool(fs))
    register(FsDeleteTool(fs))
}

class FsListTool(private val fs: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "fs_list",
        name = "List files",
        summary = "List files inside the app sandbox",
        category = ToolCategory.FILES,
        args = listOf(ToolArg("path", "string", false, "Directory, default the sandbox root"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val path = call.args["path"].orEmpty()
        val entries = fs.list(path)
        if (entries.isEmpty()) {
            "Empty directory."
        } else {
            entries.joinToString("\n") { entry ->
                val kind = if (entry.isDir) "dir " else "file"
                "$kind  ${"%8d".format(entry.bytes)}  ${entry.path}"
            }
        }
    }
}

class FsReadTool(private val fs: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "fs_read",
        name = "Read file",
        summary = "Read a UTF-8 text file from the sandbox (256 KB cap)",
        category = ToolCategory.FILES,
        args = listOf(ToolArg("path", "string", true, "File path relative to the sandbox root"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        fs.read(require(call, "path"))
    }
}

class FsWriteTool(private val fs: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "fs_write",
        name = "Write file",
        summary = "Create or append a text file inside the sandbox (512 KB cap)",
        category = ToolCategory.FILES,
        args = listOf(
            ToolArg("path", "string", true, "Target path relative to the sandbox root"),
            ToolArg("content", "string", true, "Text to write"),
            ToolArg("append", "bool", false, "true to append instead of overwrite")
        )
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val path = require(call, "path")
        val content = require(call, "content")
        val append = call.args["append"]?.toBooleanStrictOrNull() ?: false
        val size = fs.write(path, content, append)
        "${if (append) "Appended to" else "Wrote"} $path — now $size bytes"
    }
}

class FsSearchTool(private val fs: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "fs_search",
        name = "Search files",
        summary = "Grep for a substring across sandbox text files",
        category = ToolCategory.FILES,
        args = listOf(
            ToolArg("needle", "string", true, "Substring to look for"),
            ToolArg("path", "string", false, "Root directory, default the sandbox root")
        )
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val hits = fs.search(call.args["path"].orEmpty(), require(call, "needle"))
        if (hits.isEmpty()) "No matches." else hits.joinToString("\n")
    }
}

class FsDeleteTool(private val fs: SandboxFs) : BaseTool() {
    override val spec = ToolSpec(
        id = "fs_delete",
        name = "Delete file",
        summary = "Delete a file or directory inside the sandbox",
        category = ToolCategory.FILES,
        sensitive = true,
        args = listOf(ToolArg("path", "string", true, "Path relative to the sandbox root"))
    )

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        val path = require(call, "path")
        val deleted = fs.delete(path)
        if (deleted) "Deleted $path" else "Nothing deleted at $path"
    }
}
