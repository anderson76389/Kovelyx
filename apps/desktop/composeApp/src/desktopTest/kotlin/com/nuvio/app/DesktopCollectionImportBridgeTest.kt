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
    fun bundleImportIsAcceptedButNeverAppliedWithoutConfirmation() {
        DesktopCollectionImportBridge.start()
        val json = """{"format":"ma-collection-bundle-v1","collections":[{"id":"test","title":"Test","folders":[]}],"setup":{"version":1,"tmdbKey":"qa-tmdb","poster":{"provider":"rpdb","pattern":"https://api.ratingposterdb.com/t0-free-rpdb/imdb/poster-default/{imdb_id}.jpg"},"addons":[]}}"""
        val unpacked = DesktopCollectionImportBridge.unpack(json)
        assertTrue(unpacked.collections.startsWith("["))
        assertEquals("qa-tmdb", unpacked.setup?.get("tmdbKey")?.toString()?.trim('"'))
        assertEquals(202, post("https://anderson76389.github.io", json))
        assertEquals(json, DesktopCollectionImportBridge.pending.value)
        DesktopCollectionImportBridge.pending.value?.let(DesktopCollectionImportBridge::discard)
        assertEquals(null, DesktopCollectionImportBridge.pending.value)
        assertEquals(422, post("https://anderson76389.github.io", """{"format":"unknown","collections":[]}"""))
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
