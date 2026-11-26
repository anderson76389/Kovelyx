package com.kovelyx.tv.ui.screens.detail

import com.kovelyx.tv.domain.model.Video
import com.kovelyx.tv.core.util.isEpisodeReleaseAired

private val canonicalEpisodeId = Regex("^(?:tt[0-9]+|tmdb:[0-9]+):([0-9]+):([0-9]+)$", RegexOption.IGNORE_CASE)

/**
 * Uses per-episode released/available rules; one unavailable episode must not hide an entire season.
 * Anime/other addons sometimes omit seasons; their displayed virtual season
 * already contains stable synthetic (season, episode) coordinates.
 *
 * Never mark future episodes, specials, movies or live channels as watched.
 */
internal fun isEpisodeDetail(apiType: String, videos: List<Video>): Boolean =
    when (apiType.trim().lowercase()) {
        "movie", "film", "channel" -> false
        "series", "tv", "anime", "show", "tvshow" -> true
        else -> videos.isNotEmpty()
    }

/** Preserve addon numbering, recover canonical Stremio IDs, and give truly
 * unnumbered single-season lists the same stable coordinates as the TV cards. */
internal fun episodeDetailVideos(apiType: String, videos: List<Video>): List<Video> {
    if (!isEpisodeDetail(apiType, videos) || videos.all { it.season != null && it.episode != null }) return videos
    val recovered = videos.map { video ->
        val match = canonicalEpisodeId.matchEntire(video.id)
        val season = video.season ?: match?.groupValues?.get(1)?.toIntOrNull()
        val episode = video.episode ?: match?.groupValues?.get(2)?.toIntOrNull()
        if (season == video.season && episode == video.episode) video
        else video.copy(season = season, episode = episode)
    }
    // Do not reinterpret unnumbered live TV programme listings as episodes.
    if (apiType.equals("tv", ignoreCase = true)) return recovered
    if (recovered.any { it.season != null }) return recovered
    val numbers = recovered.mapNotNull { it.episode?.takeIf { n -> n > 0 } }
    val preserveNumbers = numbers.size == recovered.size && numbers.distinct().size == numbers.size
    return recovered.mapIndexed { index, video ->
        video.copy(season = 1, episode = if (preserveNumbers) video.episode else index + 1)
    }
}

/** Only use external ratings for known coordinates, never guess a provider's
 * episode order for a synthetic anime list. Addon ratings remain usable by ID. */
internal fun providerEpisodeKeys(videos: List<Video>): Set<Pair<Int, Int>> =
    videos.mapNotNull { video ->
        if (video.season != null && video.episode != null) {
            return@mapNotNull (video.season to video.episode).takeIf { video.season >= 0 && video.episode > 0 }
        }
        val match = canonicalEpisodeId.matchEntire(video.id)
        val season = video.season ?: match?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        val episode = video.episode ?: match?.groupValues?.get(2)?.toIntOrNull() ?: return@mapNotNull null
        (season to episode).takeIf { season >= 0 && episode > 0 }
    }.toSet()

internal fun releasedEpisodesForWholeSeries(
    apiType: String,
    videos: List<Video>,
    displayedEpisodes: List<Video>,
): List<Video> {
    if (!isEpisodeDetail(apiType, videos) || videos.isEmpty()) return emptyList()
    val normalized = episodeDetailVideos(apiType, videos)
    // The displayed list can supply the same virtual coordinates for old addons.
    val candidates = if (normalized.all { it.season == null }) displayedEpisodes else normalized
    return candidates.filter {
        (it.season ?: 0) > 0 && (it.episode ?: 0) > 0 &&
            it.available != false && isEpisodeReleaseAired(it.released) != false
    }.distinctBy { it.season to it.episode }
}

internal fun allReleasedEpisodesWatched(
    episodes: List<Video>,
    isWatched: (Video) -> Boolean,
): Boolean = episodes.isNotEmpty() && episodes.all(isWatched)
