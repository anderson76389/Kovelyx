package com.kovelyx.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TmdbImageUrlsTest {

    @Test
    fun `w1280 tmdb image uses original size`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/backdrop.jpg?language=en",
            originalTmdbImageUrl("https://image.tmdb.org/t/p/w1280/backdrop.jpg?language=en"),
        )
    }

    @Test
    fun `full width artwork upgrades smaller TMDB variants and preserves the path and query`() {
        for (size in listOf("w500", "w780", "h632", "original")) {
            assertEquals(
                "https://image.tmdb.org/t/p/original/art.jpg?language=fr",
                originalTmdbImageUrl("https://image.tmdb.org/t/p/$size/art.jpg?language=fr"),
            )
        }
    }

    @Test
    fun `non tmdb urls remain unchanged`() {
        val imageUrl = "https://example.com/t/p/w1280/backdrop.jpg"

        assertEquals(imageUrl, originalTmdbImageUrl(imageUrl))
    }

    @Test
    fun `null image url remains null`() {
        assertNull(originalTmdbImageUrl(null))
    }
}
