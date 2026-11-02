package com.kovelyx.cloud.preferences

/** Host/scheme are case-insensitive; paths, encoded values and query tokens are opaque. */
fun canonicalPluginRepositoryUrl(raw: String, allowJsonFile: Boolean = false): String {
    val clean = raw.trim().substringBefore('#').let { if ("://" in it) it else "https://$it" }
    val scheme = clean.substringBefore("://").lowercase().let { if (it in setOf("http", "https")) it else "https" }
    val tail = clean.substringAfter("://")
    val authority = tail.substringBefore('/').substringBefore('?')
    val credentials = authority.substringBeforeLast('@', "").let { if (it.isEmpty()) "" else "$it@" }
    val host = authority.substringAfterLast('@').lowercase()
    require(host.isNotBlank()) { "Adresse du dépôt invalide" }
    val rest = tail.removePrefix(authority)
    val path = rest.substringBefore('?').trimEnd('/')
    val manifest = if (path.endsWith("/manifest.json") || (allowJsonFile && path.endsWith(".json", true))) path else "$path/manifest.json"
    return "$scheme://$credentials$host$manifest" + if ('?' in rest) "?${rest.substringAfter('?')}" else ""
}

fun pluginCodeUrl(manifestUrl: String, filename: String): String {
    if (filename.startsWith("http://", true) || filename.startsWith("https://", true)) return filename
    val source = filename.substringBefore('#')
    val base = manifestUrl.substringBefore('?').substringBeforeLast('/')
    val query = if ('?' in source) source.substringAfter('?') else manifestUrl.substringAfter('?', "")
    val hasQuery = '?' in source || '?' in manifestUrl
    return "$base/${source.substringBefore('?').trimStart('/')}" + if (hasQuery) "?$query" else ""
}
