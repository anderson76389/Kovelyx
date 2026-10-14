package com.nuvio.app

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

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

    @Test
    fun rpdbAndConfiguredAddonsAreReadOnlyFromExplicitSettings() {
        val payload = """{"collections":[],"settings":{
            "posterProvider":"rpdb","rpdbTheme":"t0-free-rpdb-blocks",
            "addonManifests":["https://example.org/configured/manifest.json"]
        }}"""
        val config = DesktopCollectionImportBridge.extractSettings(payload)
        assertEquals(
            "https://api.ratingposterdb.com/t0-free-rpdb-blocks/imdb/poster-default/{imdb_id}.jpg",
            DesktopCollectionImportBridge.rpdbPosterPattern(config),
        )
        assertEquals(listOf("https://example.org/configured/manifest.json"),
            DesktopCollectionImportBridge.extractAddonManifests(payload))
        assertEquals(null, DesktopCollectionImportBridge.rpdbPosterPattern(emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            DesktopCollectionImportBridge.extractAddonManifests(
                """{"collections":[],"settings":{"addonManifests":["http://example.org/manifest.json"]}}"""
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCollectionImportBridge.extractAddonManifests(
                """{"collections":[],"settings":{"addonManifests":["https://example.org/not-a-manifest"]}}"""
            )
        }
    }

    @Test
    fun bttrPosterPatternAndFrenchLanguageTravelWithImport() {
        val payload = """{"collections":[],"settings":{
            "posterProvider":"bttr","bttrGenre":false,"bttrRating":true,
            "bttrQuality":true,"bttrAge":false,"bttrSource":"IM","bttrLanguage":"fr",
            "tmdbLanguage":"fr","importMode":"account","importAction":"merge"
        }}"""
        val settings = DesktopCollectionImportBridge.extractSettings(payload)
        assertEquals("fr", settings["tmdbLanguage"])
        assertEquals("account", settings["importMode"])
        assertEquals("merge", settings["importAction"])
        assertEquals(
            "https://btttr.cc/poster-rq/imdb/poster-default/{imdb_id}.jpg?lang=fr&rs=IM",
            DesktopCollectionImportBridge.posterPattern(settings),
        )
        assertEquals(
            null,
            DesktopCollectionImportBridge.posterPattern(
                mapOf("posterProvider" to "top", "topPosterKey" to "../unsafe")
            )
        )
    }

    @Test
    fun nonDestructiveImportKeepsExistingCategoryArtworkAndFolderIds() {
        val existing = """[
            {"id":"a","title":"Mes films","backdropImageUrl":"https://example.org/old.jpg",
                "folders":[{"id":"old","title":"Mes anciens films"},{"id":"shared","title":"Favoris"}]},
            {"id":"b","title":"Autres","folders":[]}
        ]"""
        val incoming = """[
            {"id":"a","title":"Nouvelle version","folders":[
                {"id":"shared","title":"Ne doit pas écraser Favoris"},
                {"id":"new","title":"Nouveaux films"}]},
            {"id":"c","title":"Nouvelle catégorie","folders":[]}
        ]"""
        val merged = DesktopCollectionImportBridge.mergeCollectionPayload(existing, incoming)
        val decoded = kotlinx.serialization.json.Json.parseToJsonElement(merged) as kotlinx.serialization.json.JsonArray
        assertEquals(3, decoded.size)
        val kept = decoded.first() as kotlinx.serialization.json.JsonObject
        assertEquals("Mes films", (kept["title"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("https://example.org/old.jpg",
            (kept["backdropImageUrl"] as kotlinx.serialization.json.JsonPrimitive).content)
        val folders = kept["folders"] as kotlinx.serialization.json.JsonArray
        assertEquals(3, folders.size)
        assertEquals("Favoris", ((folders[1] as kotlinx.serialization.json.JsonObject)["title"]
            as kotlinx.serialization.json.JsonPrimitive).content)
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
