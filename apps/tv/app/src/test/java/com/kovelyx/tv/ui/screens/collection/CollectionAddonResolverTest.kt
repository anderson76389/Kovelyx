package com.kovelyx.tv.ui.screens.collection

import com.kovelyx.tv.domain.model.Addon
import com.kovelyx.tv.domain.model.AddonCatalogCollectionSource
import com.kovelyx.tv.domain.model.CatalogDescriptor
import com.kovelyx.tv.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionAddonResolverTest {
    private fun addon(id: String, catalogId: String, type: String = "movie", enabled: Boolean = true): Addon {
        val mediaType = ContentType.fromString(type)
        return Addon(
            id = id,
            name = id,
            version = "1.0.0",
            description = "",
            logo = null,
            baseUrl = "https://example.org",
            catalogs = listOf(CatalogDescriptor(type = mediaType, rawType = type, id = catalogId, name = "Films")),
            types = listOf(mediaType),
            resources = emptyList(),
            enabled = enabled
        )
    }

    @Test
    fun importedAioSourceFindsUniqueConfiguredInstance() {
        val source = AddonCatalogCollectionSource("former-addon", "movie", "aiometa-old.tmdb.list.8679739")
        val current = addon("new-addon", "aiometa-new.tmdb.list.8679739")
        val resolved = listOf(current).resolveCollectionAddonCatalog(source)
        assertEquals(current.id, resolved?.addon?.id)
        assertEquals("aiometa-new.tmdb.list.8679739", resolved?.catalog?.id)
    }

    @Test
    fun oldAddonNameIsNotRequiredWhenCatalogIdMatchesExactly() {
        val current = addon("new-addon", "film-top-10")
        val source = AddonCatalogCollectionSource("former-addon", "movie", "film-top-10")
        assertEquals(current.id, listOf(current).resolveCollectionAddonCatalog(source)?.addon?.id)
    }

    @Test
    fun rejectDisabledAmbiguousWrongTypeAndUnrelatedSources() {
        val source = AddonCatalogCollectionSource("former-addon", "movie", "aiometa-old.tmdb.list.8679739")
        val first = addon("one", "aiometa-first.tmdb.list.8679739")
        val second = addon("two", "aiometa-second.tmdb.list.8679739")
        assertNull(listOf(first, second).resolveCollectionAddonCatalog(source))
        assertNull(listOf(first.copy(enabled = false)).resolveCollectionAddonCatalog(source))
        assertNull(listOf(addon("series", "aiometa-first.tmdb.list.8679739", "series")).resolveCollectionAddonCatalog(source))
        assertNull(listOf(addon("wrong", "aiometa-first.tmdb.list.999")).resolveCollectionAddonCatalog(source))
        assertNull("unrelated.tmdb.list.8679739".aioCollectionSemanticId())
        assertNull("aiometa-new.tmdb.list.8679739?token=private".aioCollectionSemanticId())
    }
}
