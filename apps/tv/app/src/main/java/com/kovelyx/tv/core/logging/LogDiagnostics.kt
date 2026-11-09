package com.kovelyx.tv.core.logging

// This API is used only for diagnostics, never for playback or parsing.
// Addon URLs may contain API keys even inside their encoded path segments.
// Do not retain request bodies, URLs or exception messages in on-device logs.
fun String?.rawForLog(): String =
    if (this == null) "(null)" else "[content redacted]"

fun String?.urlForLog(): String =
    if (this == null) "(null)" else "[url redacted]"

fun String?.bodySnippetForLog(maxLength: Int = Int.MAX_VALUE): String =
    if (this == null) "(null)" else "[body redacted]"

fun Throwable.diagnosticSummary(): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = this
    while (current != null && parts.size < 6) {
        val name = current.javaClass.simpleName.ifBlank { "Exception" }
        parts.add(name)
        current = current.cause
    }
    return parts.joinToString(" <- ")
}
