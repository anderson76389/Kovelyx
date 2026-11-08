package com.kovelyx.tv.core.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionCookieJarTest {
    private fun cookie(
        name: String,
        value: String,
        path: String = "/",
        secure: Boolean = false,
        domain: String = "stream.example",
    ): Cookie {
        val builder = Cookie.Builder().name(name).value(value)
            .hostOnlyDomain(domain).path(path)
        if (secure) builder.secure()
        return builder.build()
    }

    @Test
    fun `secure and path scoped cookies do not leak to a different request`() {
        val jar = ExtensionCookieJar()
        val url = "https://stream.example/private/video".toHttpUrl()
        jar.saveFromResponse(url, listOf(
            cookie("login", "secret", path = "/private", secure = true),
            cookie("public", "ok")
        ))
        assertEquals(2, jar.loadForRequest(url).size)
        assertEquals(listOf("public"), jar.loadForRequest(
            "http://stream.example/private/video".toHttpUrl()).map { it.name })
        assertEquals(listOf("public"), jar.loadForRequest(
            "https://stream.example/other".toHttpUrl()).map { it.name })
        assertTrue(jar.loadForRequest("https://unrelated.example/private/video".toHttpUrl()).isEmpty())
    }

    @Test
    fun `existing cookies still work for the same valid playback URL`() {
        val jar = ExtensionCookieJar()
        val url = "https://stream.example/video.m3u8".toHttpUrl()
        jar.saveFromResponse(url, listOf(cookie("session", "one")))
        assertEquals("one", jar.loadForRequest(url).single().value)
        jar.saveFromResponse(url, listOf(cookie("session", "two")))
        assertEquals("two", jar.loadForRequest(url).single().value)
    }

    @Test
    fun `expired cookies are discarded instead of being retained`() {
        val jar = ExtensionCookieJar()
        val url = "https://stream.example/".toHttpUrl()
        val expired = Cookie.Builder().name("session").value("old")
            .hostOnlyDomain("stream.example").expiresAt(1L).build()
        jar.saveFromResponse(url, listOf(expired))
        assertTrue(jar.loadForRequest(url).isEmpty())
        jar.saveFromResponse(url, listOf(cookie("session", "live")))
        assertEquals("live", jar.loadForRequest(url).single().value)
        jar.saveFromResponse(url, listOf(expired))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `memory is bounded by both host count and cookies per host`() {
        val jar = ExtensionCookieJar(maxHosts = 2, maxCookiesPerHost = 2)
        fun url(host: String) = "https://$host/".toHttpUrl()
        fun hostCookie(host: String, name: String) =
            cookie(name, name, domain = host)

        jar.saveFromResponse(url("one.example"), listOf(
            hostCookie("one.example", "a"),
            hostCookie("one.example", "b"),
            hostCookie("one.example", "c"),
        ))
        assertEquals(listOf("b", "c"), jar.loadForRequest(url("one.example")).map { it.name })
        jar.saveFromResponse(url("two.example"), listOf(hostCookie("two.example", "d")))
        // Touch one.example to retain it when the oldest host is evicted.
        jar.loadForRequest(url("one.example"))
        jar.saveFromResponse(url("three.example"), listOf(hostCookie("three.example", "e")))
        assertTrue(jar.loadForRequest(url("two.example")).isEmpty())
        assertEquals(2, jar.loadForRequest(url("one.example")).size)
        assertEquals(1, jar.loadForRequest(url("three.example")).size)
    }
}
