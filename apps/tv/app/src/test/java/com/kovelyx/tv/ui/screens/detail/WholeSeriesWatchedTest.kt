package com.kovelyx.tv.ui.screens.detail

import com.kovelyx.tv.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeSeriesWatchedTest {
    private fun episode(
        id: String,
        season: Int? = 1,
        number: Int? = 1,
        released: String? = null,
        available: Boolean? = true,
    ): Video = Video(
        id = id,
        title = id,
        released = released,
        thumbnail = null,
        season = season,
        episode = number,
        overview = null,
        available = available,
    )

    @Test
    fun wholeSeriesSelectsOnlyAiredEpisodesNotSpecialsOrFutureSeasons() {
        val videos = listOf(
            episode("special", season = 0),
            episode("aired", number = 1),
            episode("unavailable", number = 2, available = false),
            episode("future", season = 2, released = "2099-12-31"),
        )
        assertEquals(
            listOf("aired"),
            releasedEpisodesForWholeSeries("series", videos, videos).map { it.id },
        )
    }

    @Test
    fun animeWithoutSeasonUsesVirtualEpisodeCoordinates() {
        val source = listOf(episode("anime-a", season = null, number = null),
            episode("anime-b", season = null, number = null))
        val display = source.mapIndexed { index, item ->
            item.copy(season = 1, episode = index + 1)
        }
        assertEquals(listOf("anime-a", "anime-b"),
            releasedEpisodesForWholeSeries("anime", source, display).map { it.id })
        assertTrue(allReleasedEpisodesWatched(display) { it.episode in setOf(1, 2) })
        assertFalse(allReleasedEpisodesWatched(display) { it.episode == 1 })
    }

    @Test
    fun unavailableFirstEpisodeDoesNotHideReleasedEpisodesAndCanonicalIdsRestoreCoordinates() {
        val source = listOf(
            episode("tt1234567:1:1", season = null, number = null, available = false),
            episode("tt1234567:1:2", season = null, number = null),
            episode("tt1234567:2:1", season = null, number = null, released = "2099-12-31"),
        )
        assertEquals(setOf(1 to 1, 1 to 2, 2 to 1), providerEpisodeKeys(source))
        assertEquals(listOf("tt1234567:1:2"),
            releasedEpisodesForWholeSeries("anime", source, emptyList()).map { it.id })
    }

    @Test
    fun unnumberedAnimeIsVisibleButNeverReceivesGuessedExternalRatings() {
        val source = listOf(episode("anime-a", season = null, number = null),
            episode("anime-b", season = null, number = null))
        assertEquals(listOf(1 to 1, 1 to 2), episodeDetailVideos("anime", source).map { it.season to it.episode })
        assertTrue(providerEpisodeKeys(source).isEmpty())
        assertTrue(isEpisodeDetail("anime", source))
    }

    @Test
    fun moviesChannelsAndEmptySeriesDoNotExposeBulkAction() {
        val episodes = listOf(episode("episode"))
        assertTrue(releasedEpisodesForWholeSeries("movie", episodes, episodes).isEmpty())
        assertTrue(releasedEpisodesForWholeSeries("channel", episodes, episodes).isEmpty())
        assertTrue(releasedEpisodesForWholeSeries("series", emptyList(), episodes).isEmpty())
        assertFalse(allReleasedEpisodesWatched(emptyList()) { true })
    }
}
