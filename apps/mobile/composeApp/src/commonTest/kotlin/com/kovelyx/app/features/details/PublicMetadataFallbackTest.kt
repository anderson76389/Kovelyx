package com.kovelyx.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicMetadataFallbackTest {
    @Test
    fun buildsSafeMovieAndSeriesUrlsForImdbIds() {
        assertEquals(
            "https://v3-cinemeta.strem.io/meta/movie/tt0133093.json",
            PublicMetadataFallback.buildMetadataUrl("movie", "tt0133093"),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/meta/series/tt0944947.json",
            PublicMetadataFallback.buildMetadataUrl("series", "imdb:tt0944947:2:3"),
        )
        assertEquals(
            "https://v3-cinemeta.strem.io/meta/series/tt0944947.json",
            PublicMetadataFallback.buildMetadataUrl("tv", "tt0944947"),
        )
    }

    @Test
    fun rejectsOtherIdsUnsafePathsAndUnsupportedMedia() {
        assertNull(PublicMetadataFallback.buildMetadataUrl("movie", "tmdb:603"))
        assertNull(PublicMetadataFallback.buildMetadataUrl("movie", "../../admin"))
        assertNull(PublicMetadataFallback.buildMetadataUrl("movie", "tt0133093?debug=true"))
        assertNull(PublicMetadataFallback.buildMetadataUrl("channel", "tt0133093"))
        assertNull(PublicMetadataFallback.buildMetadataUrl("movie", "https://evil.example"))
    }

    @Test
    fun acceptsCorrectStremioMetaResponse() {
        val payload = """{"meta":{"id":"tt0133093","type":"movie","name":"Matrix","description":"Résumé","poster":"https://example.test/poster.jpg"}}"""
        val parsed = PublicMetadataFallback.parseMetadataPayload(payload, "movie", "tt0133093")
        assertEquals("Matrix", parsed?.name)
        assertTrue(!parsed?.description.isNullOrBlank())
    }

    @Test
    fun rejectsWrongItemOrWrongKind() {
        val payload = """{"meta":{"id":"tt0944947","type":"series","name":"Other show"}}"""
        assertNull(PublicMetadataFallback.parseMetadataPayload(payload, "movie", "tt0944947"))
        assertNull(PublicMetadataFallback.parseMetadataPayload(payload, "series", "tt0133093"))
    }
}
