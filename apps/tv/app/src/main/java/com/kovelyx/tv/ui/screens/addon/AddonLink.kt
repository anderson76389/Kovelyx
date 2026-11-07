package com.kovelyx.tv.ui.screens.addon

/** Keep personal path/query configuration intact when sharing an installed addon. */
internal fun addonManifestLink(baseUrl: String): String {
    val queryAt = baseUrl.indexOf('?')
    val path = (if (queryAt >= 0) baseUrl.substring(0, queryAt) else baseUrl).trimEnd('/')
    val query = if (queryAt >= 0) baseUrl.substring(queryAt) else ""
    return (if (path.endsWith("/manifest.json", ignoreCase = true)) path else "$path/manifest.json") + query
}
