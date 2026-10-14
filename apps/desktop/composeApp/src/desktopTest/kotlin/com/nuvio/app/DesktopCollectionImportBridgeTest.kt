package com.nuvio.app

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCollectionImportBridgeTest {
    @Test
    fun trustedLocalImportStaysPendingUntilUserConfirms() {
        DesktopCollectionImportBridge.start()
        assertTrue(DesktopCollectionImportBridge.alreadyRunning())

        val blocked = post("https://untrusted.example", "[{\"id\":\"test\"}]")
        assertEquals(403, blocked)
        assertEquals(null, DesktopCollectionImportBridge.pending.value)

        val allowed = post(
            "https://anderson76389.github.io",
            "[{\"id\":\"test\",\"title\":\"Test\",\"folders\":[]}]",
        )
        assertEquals(202, allowed)
        assertNotNull(DesktopCollectionImportBridge.pending.value)
        assertEquals(409, post("https://anderson76389.github.io", "[]"))

        DesktopCollectionImportBridge.pending.value?.let(DesktopCollectionImportBridge::discard)
        assertEquals(null, DesktopCollectionImportBridge.pending.value)
    }

    @Test
    fun setupEnvelopeKeepsCredentialsSeparateFromCollectionsAndRequiresConfirmation() {
        DesktopCollectionImportBridge.start()
        val payload = """{"collections":[{"id":"one","title":"Une","folders":[]}],
            "settings":{"tmdbKey":"tmdb-private","mdblistKey":"mdb-private","realDebridKey":"rd-private"}}"""
        val extracted = DesktopCollectionImportBridge.extractCollectionJson(payload)
        assertEquals("""[{"id":"one","title":"Une","folders":[]}]""", extracted)
        assertTrue(!extracted.orEmpty().contains("private"))
        assertEquals(
            mapOf("tmdbKey" to "tmdb-private","mdblistKey" to "mdb-private","realDebridKey" to "rd-private"),
            DesktopCollectionImportBridge.extractSettings(payload),
        )
        assertEquals(202, post("https://anderson76389.github.io", payload))
        assertNotNull(DesktopCollectionImportBridge.pending.value)
        DesktopCollectionImportBridge.pending.value?.let(DesktopCollectionImportBridge::discard)
        assertEquals(422, post("https://anderson76389.github.io", """{"settings":{"tmdbKey":"bad"}}"""))
        assertEquals(null, DesktopCollectionImportBridge.pending.value)
    }

    private fun post(origin: String, body: String): Int {
        // HttpURLConnection drops Origin as a restricted header on some JDKs.
        // java.net.http sets it explicitly, like a browser CORS request.
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:38761/import"))
            .header("Origin", origin)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.discarding())
            .statusCode()
    }
}
