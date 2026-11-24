package com.kovelyx.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeRatingSourceTest {
    @Test fun preservesSourceAndRejectsInvalidScores() {
        assertEquals(EpisodeRating(8.7, "TMDB"), validEpisodeRating(8.7, "TMDB"))
        assertEquals(EpisodeRating(9.1, "IMDb"), validEpisodeRating(9.1, "IMDb"))
        assertNull(validEpisodeRating(0.0, "TMDB"))
        assertNull(validEpisodeRating(10.1, "IMDb"))
        assertNull(validEpisodeRating(Double.NaN, "IMDb"))
        assertNull(validEpisodeRating(null, "Addon"))
    }
}
