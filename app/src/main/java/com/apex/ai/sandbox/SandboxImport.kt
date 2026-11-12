package com.apex.ai.sandbox

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.apex.ai.core.ApexLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a document the user picked via the system file picker into the sandbox
 * so the analysis tools can read it. This is the only inbound path into the
 * sandbox that is not model-driven, which is why it always requires a user tap.
 */
suspend fun SandboxFs.importFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val displayName = queryName(context, uri) ?: "import_${System.currentTimeMillis()}.bin"
    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    val target = File(root, safeName)
    val stream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Cannot open the selected file")
    stream.use { input ->
        target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
    }
    ApexLog.d("Sandbox", "imported $safeName (${target.length()} bytes)")
    safeName
}

private fun queryName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()
