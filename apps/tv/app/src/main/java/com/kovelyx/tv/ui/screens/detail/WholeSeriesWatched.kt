package com.kovelyx.tv.ui.screens.detail

import com.kovelyx.tv.domain.model.Video
import com.kovelyx.tv.domain.model.watchableEpisodes

/**
 * Uses the same released/available episode rules as the normal TV episode list.
 * Anime/other addons sometimes omit seasons; their displayed virtual season
 * already contains stable synthetic (season, episode) coordinates.
 *
 * Never mark future episodes, specials, movies or live channels as watched.
 */
internal fun releasedEpisodesForWholeSeries(
    apiType: String,
    videos: List<Video>,
    displayedEpisodes: List<Video>,
): List<Video> {
    if (apiType.equals("movie", ignoreCase = true) ||
        apiType.equals("channel", ignoreCase = true) ||
        videos.isEmpty()
    ) return emptyList()
    val normalized = if (videos.all { it.season == null }) displayedEpisodes else videos
    return normalized.watchableEpisodes().distinctBy { (it.season ?: 0) to (it.episode ?: 0) }
}

internal fun allReleasedEpisodesWatched(
    episodes: List<Video>,
    isWatched: (Video) -> Boolean,
): Boolean = episodes.isNotEmpty() && episodes.all(isWatched)
