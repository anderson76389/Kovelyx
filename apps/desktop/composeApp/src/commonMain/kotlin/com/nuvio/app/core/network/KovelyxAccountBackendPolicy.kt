package com.nuvio.app.core.network

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Guard both the visible account form and auth requests.
 * A public account password must never go to the old Nuvio server,
 * to an unconfigured backend, or to an insecure public HTTP endpoint.
 */
internal fun ServerConfiguration.isKovelyxAccountBackendReady(): Boolean {
    if (!capabilities.emailPasswordAuth || backendUrl.isBlank() || publishableKey.isBlank()) return false
    if (publishableKey.trim().startsWith("sb_secret_", ignoreCase = true)) return false
    val url = runCatching { Url(backendUrl) }.getOrNull() ?: return false
    val host = url.host.lowercase()
    if (host.isBlank() || host == "nuvio.tv" || host.endsWith(".nuvio.tv") ||
        host == "nuvio.app" || host.endsWith(".nuvio.app")) return false
    val isLoopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
    return url.protocol == URLProtocol.HTTPS || (isLoopback && url.protocol == URLProtocol.HTTP)
}
