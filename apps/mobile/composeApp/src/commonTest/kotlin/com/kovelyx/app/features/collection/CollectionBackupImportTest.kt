package com.kovelyx.app.features.collection

import kotlin.test.*
import kotlinx.serialization.json.*
import com.kovelyx.app.features.addons.*

class CollectionBackupImportTest {
    @Test
    fun `merge retains unknown fields and unrelated collections`() {
        val current = """[{"id":"keep","title":"Existing","futureOption":true},{"id":"replace","title":"Old"}]"""
        val incoming = """[{"id":"replace","title":"New","folders":[],"futureArtwork":"preserved"}]"""
        val result = Json.parseToJsonElement(mergeCollectionImportJson(current, incoming)).jsonArray
        assertEquals(2, result.size)
        assertEquals(JsonPrimitive(true), result[0].jsonObject["futureOption"])
        assertEquals(JsonPrimitive("New"), result[1].jsonObject["title"])
        assertEquals(JsonPrimitive("preserved"), result[1].jsonObject["futureArtwork"])
    }

    private fun addon(id: String, catalogId: String, type: String = "movie", enabled: Boolean = true) = ManagedAddon(
        manifestUrl = "https://example.com/$id/manifest.json",
        enabled = enabled,
        manifest = AddonManifest(id = id, name = id, description = "", version = "1", resources = emptyList(),
            types = listOf(type), catalogs = listOf(AddonCatalog(type, catalogId, "Films")), transportUrl = "https://example.com"),
    )

    @Test
    fun `configured AIO instance can replace expired instance only when unambiguous`() {
        val source = CollectionCatalogSource("old-addon", "movie", "aiometa-old.tmdb.list.123")
        val active = addon("new-addon", "aiometa-new.tmdb.list.123")
        assertEquals("aiometa-new.tmdb.list.123", listOf(active).findCollectionCatalog(source)?.catalog?.id)
        assertNull(listOf(active, addon("second", "aiometa-second.tmdb.list.123")).findCollectionCatalog(source))
        assertNull(listOf(addon("disabled", "aiometa-new.tmdb.list.123", enabled = false)).findCollectionCatalog(source))
        assertNull(listOf(addon("wrong-list", "aiometa-new.tmdb.list.999")).findCollectionCatalog(source))
        assertNull(listOf(addon("wrong-type", "aiometa-new.tmdb.list.123", type = "series")).findCollectionCatalog(source))
    }

    @Test
    fun `unrecognized addon prefixes never match by suffix`() {
        assertNull("unrelated.tmdb.list.123".aioMetadataCatalogKey())
        assertNull("aiometa-new.tmdb.list.123?secret".aioMetadataCatalogKey())
        assertEquals("tmdb.discover.netflix_movies", "aiometa-new.tmdb.discover.netflix_movies".aioMetadataCatalogKey())
    }
}
