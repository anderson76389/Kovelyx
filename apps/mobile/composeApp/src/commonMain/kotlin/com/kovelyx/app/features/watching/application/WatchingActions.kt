package com.kovelyx.app.features.watching.application

import com.kovelyx.app.features.details.MetaDetails
import com.kovelyx.app.features.details.MetaDetailsRepository
import com.kovelyx.app.features.details.MetaVideo
import com.kovelyx.app.features.home.MetaPreview
import com.kovelyx.app.features.watched.WatchedItem
import com.kovelyx.app.features.watched.WatchedRepository
import com.kovelyx.app.features.watched.episodePlaybackId
import com.kovelyx.app.features.watched.releasedMainSeasonEpisodes
import com.kovelyx.app.features.watched.releasedPlayableEpisodes
import com.kovelyx.app.features.watched.toEpisodeWatchedItem
import com.kovelyx.app.features.watched.toSeriesWatchedItem
import com.kovelyx.app.features.watched.toWatchedItem
import com.kovelyx.app.features.watchprogress.CurrentDateProvider
import com.kovelyx.app.features.watchprogress.WatchProgressEntry
import com.kovelyx.app.features.watchprogress.WatchProgressRepository
import com.kovelyx.app.features.watching.domain.isSeriesLikeWatchingContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WatchingActions {
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun togglePosterWatched(preview: MetaPreview) {
        if (!preview.type.isSeriesLikeWatchingContentType(includeAnime = true)) {
            WatchedRepository.toggleWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
            return
        }

        val isCurrentlyWatched = WatchedRepository.isWatched(
            id = preview.id,
            type = preview.type,
        ) || WatchedRepository.isFullyWatchedSeries(id = preview.id, type = preview.type)
        val meta = MetaDetailsRepository.fetch(type = preview.type, id = preview.id)
        val isAnime = preview.type.equals("anime", ignoreCase = true) || meta?.type.equals("anime", ignoreCase = true)
        if (meta == null) {
            // Anime without reachable metadata must keep the previous poster-only
            // toggle; do not turn a working action into a no-op when offline.
            if (isAnime) {
                WatchedRepository.toggleWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
                return
            }
            if (isCurrentlyWatched) {
                WatchedRepository.unmarkWatched(preview.toWatchedItem(markedAtEpochMs = 0L))
                WatchedRepository.updateFullyWatchedSeries(
                    id = preview.id,
                    type = preview.type,
                    isFullyWatched = false,
                )
            }
            return
        }

        val todayIsoDate = CurrentDateProvider.todayIsoDate()
        // Some anime providers index episodes but omit the season entirely.
        // Keep those original coordinates rather than fabricating S01, and
        // ignore specials/unreleased or unnumbered videos.
        val releasedMainEpisodes = if (isAnime) {
            meta.releasedPlayableEpisodes(todayIsoDate).filter { video ->
                video.episode != null && (video.season == null || video.season > 0)
            }
        } else {
            meta.releasedMainSeasonEpisodes(todayIsoDate)
        }
        if (releasedMainEpisodes.isEmpty()) {
            if (isAnime) {
                // If the addon exposes no indexed episodes, preserve the
                // historical poster toggle rather than silently doing nothing.
                if (isCurrentlyWatched) {
                    WatchedRepository.unmarkWatched(meta.toSeriesWatchedItem())
                    WatchedRepository.updateFullyWatchedSeries(
                        id = meta.id, type = meta.type, isFullyWatched = false,
                    )
                } else {
                    WatchedRepository.markWatched(meta.toSeriesWatchedItem())
                }
                return
            }
            if (isCurrentlyWatched) {
                WatchedRepository.unmarkWatched(meta.toSeriesWatchedItem())
                WatchedRepository.updateFullyWatchedSeries(
                    id = meta.id,
                    type = meta.type,
                    isFullyWatched = false,
                )
            }
            return
        }
        val seriesItems = buildList {
            add(meta.toSeriesWatchedItem())
            addAll(releasedMainEpisodes.map(meta::toEpisodeWatchedItem))
        }

        if (isCurrentlyWatched) {
            WatchedRepository.unmarkWatched(seriesItems)
            WatchProgressRepository.clearProgress(
                videoIds = releasedMainEpisodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
            WatchedRepository.updateFullyWatchedSeries(
                id = meta.id,
                type = meta.type,
                isFullyWatched = false,
            )
        } else {
            WatchedRepository.markWatched(seriesItems)
            WatchedRepository.updateFullyWatchedSeries(
                id = meta.id,
                type = meta.type,
                isFullyWatched = true,
            )
            WatchProgressRepository.clearProgress(
                videoIds = releasedMainEpisodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        }
    }

    fun toggleEpisodeWatched(
        meta: MetaDetails,
        episode: MetaVideo,
        isCurrentlyWatched: Boolean,
    ) {
        val watchedItem = meta.toEpisodeWatchedItem(episode)
        if (isCurrentlyWatched) {
            WatchedRepository.unmarkWatched(watchedItem)
            WatchProgressRepository.clearProgress(
                videoId = meta.episodePlaybackId(episode),
                parentMetaId = meta.id,
            )
        } else {
            WatchedRepository.markWatched(watchedItem)
            WatchProgressRepository.clearProgress(
                videoId = meta.episodePlaybackId(episode),
                parentMetaId = meta.id,
            )
        }
        reconcileSeriesWatchedState(meta)
    }

    fun togglePreviousEpisodesWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        toggleEpisodesWatched(
            meta = meta,
            episodes = episodes,
            areCurrentlyWatched = areCurrentlyWatched,
        )
    }

    fun toggleSeasonWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        toggleEpisodesWatched(
            meta = meta,
            episodes = episodes,
            areCurrentlyWatched = areCurrentlyWatched,
        )
    }

    fun reconcileSeriesWatchedState(
        meta: MetaDetails,
        todayIsoDate: String = CurrentDateProvider.todayIsoDate(),
    ) {
        if (!meta.type.isSeriesLikeWatchingContentType(includeAnime = true)) return

        WatchedRepository.reconcileSeriesWatchedState(
            meta = meta,
            todayIsoDate = todayIsoDate,
            isEpisodeCompleted = { episode ->
                WatchProgressRepository.progressForVideo(
                    videoId = meta.episodePlaybackId(episode),
                    parentMetaId = meta.id,
                    seasonNumber = episode.season,
                    episodeNumber = episode.episode,
                )?.isCompleted == true
            },
        )
    }

    fun onProgressEntryUpdated(entry: WatchProgressEntry, syncRemote: Boolean = true) {
        if (!entry.isCompleted) return

        val watchedItem = WatchedItem(
            id = entry.parentMetaId,
            type = entry.parentMetaType,
            name = entry.title,
            poster = entry.poster,
            season = entry.seasonNumber,
            episode = entry.episodeNumber,
            markedAtEpochMs = entry.lastUpdatedEpochMs,
        )
        WatchedRepository.markWatchedFromPlaybackCompletion(watchedItem, syncRemote = syncRemote)

        if (!syncRemote || !entry.isEpisode) return
        actionScope.launch {
            val meta = runCatching {
                MetaDetailsRepository.fetch(
                    type = entry.parentMetaType,
                    id = entry.parentMetaId,
                )
            }.getOrNull() ?: return@launch

            reconcileSeriesWatchedState(meta = meta)
        }
    }

    private fun toggleEpisodesWatched(
        meta: MetaDetails,
        episodes: Collection<MetaVideo>,
        areCurrentlyWatched: Boolean,
    ) {
        if (episodes.isEmpty()) return
        val watchedItems = episodes.map(meta::toEpisodeWatchedItem)
        if (areCurrentlyWatched) {
            WatchedRepository.unmarkWatched(watchedItems)
            WatchProgressRepository.clearProgress(
                videoIds = episodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        } else {
            WatchedRepository.markWatched(watchedItems)
            WatchProgressRepository.clearProgress(
                videoIds = episodes.map(meta::episodePlaybackId),
                parentMetaId = meta.id,
            )
        }
        reconcileSeriesWatchedState(meta)
    }
}

