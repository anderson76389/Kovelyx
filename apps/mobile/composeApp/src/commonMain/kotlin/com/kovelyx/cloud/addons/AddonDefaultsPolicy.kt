package com.kovelyx.cloud.addons

const val ADDON_DEFAULTS_VERSION = 1
const val OPENSUBTITLES_V3_MANIFEST = "https://opensubtitles-v3.strem.io/manifest.json"

/** Preserve opaque configuration path/query bytes; only the scheme and host are case insensitive. */
fun canonicalAddonManifestUrl(raw: String): String {
    val trimmed = raw.trim().substringBefore('#')
    val clean = if ("://" in trimmed) trimmed else "https://$trimmed"
    val scheme = clean.substringBefore("://").lowercase().let { if (it == "stremio") "https" else it }
    val tail = clean.substringAfter("://", clean)
    val authority = tail.substringBefore('/').substringBefore('?')
    val credentials = authority.substringBeforeLast('@', "").let { if (it.isEmpty()) "" else "$it@" }
    val hostAndPort = authority.substringAfterLast('@').lowercase()
    val rest = tail.removePrefix(authority)
    val query = rest.substringAfter('?', "")
    val path = rest.substringBefore('?').trimEnd('/')
    val manifestPath = if (path.endsWith("/manifest.json")) path else "$path/manifest.json"
    return "$scheme://$credentials$hostAndPort$manifestPath" + if ('?' in rest) "?$query" else ""
}

/** Fingerprints identify obsolete defaults for removal without retaining an old service endpoint. */
class AddonDefaultsPolicy(private val sha256: (String) -> String) {
    private val retiredHosts = setOf(
        "2e941e9a17d4254f4fe526597355fb42781e5d12aece5ac78682f1158bb655d8",
        "4b23ff8bea8c7908f1640c57baa60babbc890af5e96526b6a825b1ac50e87029",
    )
    fun retired(url: String): Boolean {
        val authority = url.trim().substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return host.isNotEmpty() && sha256(host) in retiredHosts
    }
    fun clean(items: List<CloudAddon>): List<CloudAddon> = items.filterNot { retired(it.url) }
    fun seed(items: List<CloudAddon>): List<CloudAddon> {
        val clean = clean(items)
        return if (clean.any { canonicalAddonManifestUrl(it.url).substringAfter("://").substringBefore('/').substringBefore('?') == "opensubtitles-v3.strem.io" }) clean
        else clean + CloudAddon(OPENSUBTITLES_V3_MANIFEST)
    }
}

/** Separate the path before trimming; opaque query values can legitimately end in a slash. */
fun splitConfiguredAddonBaseUrl(raw: String): Pair<String, String> {
    val clean = raw.trim().substringBefore('#')
    val index = clean.indexOf('?')
    val path = (if (index < 0) clean else clean.substring(0, index)).trimEnd('/')
    return path to if (index < 0) "" else clean.substring(index)
}
