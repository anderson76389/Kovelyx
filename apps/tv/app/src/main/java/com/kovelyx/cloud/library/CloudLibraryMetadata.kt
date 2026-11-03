package com.kovelyx.cloud.library

import kotlinx.serialization.json.*

/** Enrich an equal-timestamp snapshot without overwriting another device's metadata. */
fun enrichLibraryMetadata(remote: JsonObject, local: JsonObject): JsonObject = JsonObject(remote.toMutableMap().apply {
    for ((key, value) in local) {
        val previous = get(key)
        val missing = previous == null || previous is JsonNull ||
            (previous is JsonPrimitive && previous.isString && previous.content.isBlank()) ||
            (previous is JsonArray && previous.isEmpty())
        if (missing && value !is JsonNull) put(key, value)
    }
})
