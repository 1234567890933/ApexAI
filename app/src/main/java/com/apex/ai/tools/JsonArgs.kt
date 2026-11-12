package com.apex.ai.tools

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Tolerant JSON-object to flat-string-map conversion for tool arguments. */
object JsonArgs {

    fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val element: JsonElement = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        val out = LinkedHashMap<String, String>()
        element.asJsonObject.entrySet().forEach { (key, value) ->
            out[key] = when {
                value.isJsonNull -> ""
                value.isJsonPrimitive -> value.asJsonPrimitive.asString
                else -> value.toString()
            }
        }
        return out
    }
}
