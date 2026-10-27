package com.kovelyx.app.core.ui

import androidx.compose.runtime.Composable
import com.kovelyx.app.features.cloud.CloudLibraryContentType
import com.kovelyx.app.core.i18n.localizedEpisodeTitle
import com.kovelyx.app.features.watchprogress.ContinueWatchingItem
import kovelyx.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedContinueWatchingSubtitle(item: ContinueWatchingItem, compact: Boolean = false): String {
    val seasonNumber = item.seasonNumber
    val episodeNumber = item.episodeNumber
    val episodeTitle = localizedEpisodeTitle(item.episodeTitle, item.episodeNumber)

    val base = when {
        seasonNumber != null && episodeNumber != null ->
            stringResource(Res.string.compose_player_episode_code_full, seasonNumber, episodeNumber)
        item.parentMetaType.equals(CloudLibraryContentType, ignoreCase = true) ->
            stringResource(Res.string.library_source_cloud)
        else ->
            stringResource(Res.string.media_movie)
    }

    return episodeTitle?.let { "$base • $it" } ?: base
}
