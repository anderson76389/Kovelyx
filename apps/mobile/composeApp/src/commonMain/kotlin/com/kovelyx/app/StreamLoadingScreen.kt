package com.kovelyx.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.kovelyx.app.features.player.OpeningOverlay
import com.kovelyx.app.features.player.subtitleLoadingStatusMessage
import com.kovelyx.app.features.streams.StreamLaunch
import com.kovelyx.app.features.streams.StreamsUiState
import kovelyx.composeapp.generated.resources.Res
import kovelyx.composeapp.generated.resources.debrid_resolving_stream
import kovelyx.composeapp.generated.resources.player_loading_preparing
import kovelyx.composeapp.generated.resources.streams_finding_source
import kovelyx.composeapp.generated.resources.streams_loading_subtitles
import kovelyx.composeapp.generated.resources.youtube_resolving_stream
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StreamLoadingScreen(
    launch: StreamLaunch,
    state: StreamsUiState,
    showStatus: Boolean,
    resolvingDebridStream: Boolean,
    resolvingYouTubeStream: Boolean = false,
    onBack: () -> Unit,
    preparingPlayback: Boolean = false,
) {
    val message = when {
        !showStatus -> null
        resolvingDebridStream -> stringResource(Res.string.debrid_resolving_stream)
        resolvingYouTubeStream -> stringResource(Res.string.youtube_resolving_stream)
        preparingPlayback -> stringResource(Res.string.player_loading_preparing)
        state.overlayMessage == stringResource(Res.string.streams_loading_subtitles) -> subtitleLoadingStatusMessage()
        state.overlayMessage != null -> state.overlayMessage
        state.autoPlayStream != null -> stringResource(Res.string.player_loading_preparing)
        else -> stringResource(Res.string.streams_finding_source)
    }
    OpeningOverlay(
        artwork = launch.background ?: launch.poster,
        logo = launch.logo,
        title = launch.title,
        onBack = onBack,
        horizontalSafePadding = 0.dp,
        message = message,
    )
}
