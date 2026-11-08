package com.kovelyx.tv.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory cookies shared by the extension scraper and its playback client.
 * The existing exact-host isolation is retained; only cookies matching the
 * request scheme, domain and path may be sent to the player.
 *
 * Both host count and cookies per host are bounded to avoid retaining unbounded
 * extension cookies during long-running Android TV sessions.
 */
internal class ExtensionCookieJar(
    private val maxHosts: Int = 128,
    private val maxCookiesPerHost: Int = 64,
) : CookieJar {
    init {
        require(maxHosts > 0)
        require(maxCookiesPerHost > 0)
    }

    private val lock = Any()
    private val store = LinkedHashMap<String, MutableList<Cookie>>(16, 0.75f, true)

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val cookies = store[url.host] ?: return@synchronized emptyList()
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        if (cookies.isEmpty()) {
            store.remove(url.host)
            return@synchronized emptyList()
        }
        // Cookie.matches enforces Secure, Domain and Path restrictions.
        cookies.filter { it.matches(url) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            if (cookies.isEmpty()) return@synchronized
            val now = System.currentTimeMillis()
            val hostCookies = store.getOrPut(url.host) { mutableListOf() }
            hostCookies.removeAll { it.expiresAt <= now }
            for (cookie in cookies) {
                // Retain the historical single-cookie-per-name replacement behavior.
                hostCookies.removeAll { it.name == cookie.name }
                if (cookie.expiresAt > now) {
                    hostCookies.add(cookie)
                }
            }
            while (hostCookies.size > maxCookiesPerHost) {
                hostCookies.removeAt(0)
            }
            if (hostCookies.isEmpty()) store.remove(url.host)
            while (store.size > maxHosts) {
                val oldest = store.keys.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
        }
    }
}
