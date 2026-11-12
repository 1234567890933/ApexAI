package com.apex.ai.core

/**
 * JNI surface for the C++ core.
 *
 * Every call has a Kotlin fallback: a device that refuses to load the shared
 * library must still get a working app, just slower on large files.
 */
object NativeBridge {

    val available: Boolean = runCatching { System.loadLibrary("apexcore") }.isSuccess

    external fun engineVersion(): String
    external fun entropy(data: ByteArray): Double
    external fun extractStrings(data: ByteArray, minLength: Int, limit: Int): Array<String>

    fun versionLabel(): String = if (available) {
        runCatching { engineVersion() }.getOrDefault("native core (unavailable)")
    } else {
        "kotlin fallback"
    }

    fun shannonEntropy(data: ByteArray): Double {
        if (available && data.isNotEmpty()) {
            runCatching { return entropy(data) }
        }
        if (data.isEmpty()) return 0.0
        val counts = IntArray(256)
        data.forEach { counts[it.toInt() and 0xFF]++ }
        var e = 0.0
        for (count in counts) {
            if (count == 0) continue
            val p = count.toDouble() / data.size
            e -= p * (Math.log(p) / Math.log(2.0))
        }
        return e
    }

    fun printableStrings(data: ByteArray, minLength: Int, limit: Int): List<String> {
        if (available && data.isNotEmpty()) {
            runCatching { return extractStrings(data, minLength, limit).toList() }
        }
        val out = LinkedHashSet<String>()
        val builder = StringBuilder()
        for (byte in data) {
            val value = byte.toInt() and 0xFF
            if (value in 32..126) {
                builder.append(value.toChar())
                if (builder.length > 512) builder.setLength(0)
            } else {
                if (builder.length >= minLength) out.add(builder.toString())
                builder.setLength(0)
                if (out.size >= limit) break
            }
        }
        if (out.size < limit && builder.length >= minLength) out.add(builder.toString())
        return out.toList()
    }
}
