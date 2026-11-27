package com.kovelyx.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kovelyx.app.core.ui.KovelyxToastController
import com.kovelyx.app.features.player.ExternalPlayerIntentResult
import com.kovelyx.app.features.settings.ExternalPlayerSelectionDialog
import com.kovelyx.app.features.player.ExternalPlayerPlaybackRequest
import com.kovelyx.app.features.player.incompatibleWithVlc
import com.kovelyx.app.features.player.AndroidPlaybackEngine
import com.kovelyx.app.features.player.ExternalPlayerApp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.kovelyx.app.features.player.ExternalPlayerPlatform
import com.kovelyx.app.features.player.PlayerLaunch
import com.kovelyx.app.features.player.PlayerLaunchStore
import com.kovelyx.app.features.player.PlayerScreen
import com.kovelyx.app.features.watchprogress.ResumePromptRepository
import com.kovelyx.app.navigation.KovelyxNavigator
import com.kovelyx.app.navigation.PlayerRoute

@Composable
internal fun PlayerDestination(
    route: PlayerRoute,
    navController: KovelyxNavigator,
    externalPlayerId: String?,
    externalPlayerNotConfiguredText: String,
    externalPlayerFailedText: String,
    onExternalPlayerLaunch: (PlayerLaunch) -> Unit,
    launchExternalPlayer: (ExternalPlayerIntentResult.Success) -> Boolean,
    openExternalStreamUrl: (String) -> Boolean,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val launch = remember(route.launchId) { PlayerLaunchStore.get(route.launchId) }
    if (launch == null) {
        LaunchedEffect(route.launchId) {
            onBack()
        }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    LaunchedEffect(launch.videoId) {
        launch.videoId?.let { ResumePromptRepository.markPlayerEntered(it) }
    }
    var pendingExternal by remember(route.launchId) { mutableStateOf<ExternalPlayerPlaybackRequest?>(null) }
    var activeEngine by remember(route.launchId) { mutableStateOf<AndroidPlaybackEngine?>(null) }
    var switchEngine by remember(route.launchId) { mutableStateOf<(() -> Unit)?>(null) }
    val openPicker: (ExternalPlayerPlaybackRequest, AndroidPlaybackEngine?, () -> Unit) -> Unit = { request, engine, change ->
        activeEngine = engine
        switchEngine = change
        pendingExternal = request
    }
    PlayerScreen(
        profileId = launch.profileId,
        title = launch.title,
        sourceUrl = launch.sourceUrl,
        sourceAudioUrl = launch.sourceAudioUrl,
        sourceHeaders = launch.sourceHeaders,
        sourceResponseHeaders = launch.sourceResponseHeaders,
        externalSubtitles = launch.externalSubtitles,
        streamType = launch.streamType,
        logo = launch.logo,
        poster = launch.poster,
        background = launch.background,
        seasonNumber = launch.seasonNumber,
        episodeNumber = launch.episodeNumber,
        episodeTitle = launch.episodeTitle,
        episodeThumbnail = launch.episodeThumbnail,
        streamTitle = launch.streamTitle,
        streamSubtitle = launch.streamSubtitle,
        initialBingeGroup = launch.bingeGroup,
        pauseDescription = launch.pauseDescription,
        providerName = launch.providerName,
        providerAddonId = launch.providerAddonId,
        contentType = launch.contentType,
        videoId = launch.videoId,
        parentMetaId = launch.parentMetaId,
        parentMetaType = launch.parentMetaType,
        torrentInfoHash = launch.torrentInfoHash,
        torrentFileIdx = launch.torrentFileIdx,
        torrentFilename = launch.torrentFilename,
        torrentTrackers = launch.torrentTrackers,
        initialPositionMs = launch.initialPositionMs,
        initialProgressFraction = launch.initialProgressFraction,
        contentLanguage = launch.contentLanguage,
        launchId = route.launchId,
        onBack = onBack,
        onOpenInExternalPlayer = openPicker,
        onOpenExternalUrl = { url ->
            openExternalStreamUrl(url)
        },
        modifier = Modifier.fillMaxSize(),
    )

    pendingExternal?.let { request ->
        val other = if (activeEngine == AndroidPlaybackEngine.Libmpv) AndroidPlaybackEngine.ExoPlayer else AndroidPlaybackEngine.Libmpv
        val currentLabel = if (activeEngine == AndroidPlaybackEngine.Libmpv) "libmpv (β)" else activeEngine?.label ?: stringResource(Res.string.settings_playback_player_preference_internal)
        val available = remember(request) { ExternalPlayerPlatform.availablePlayers() }
        val warning = stringResource(Res.string.player_vlc_incompatible_headers)
        val choices = buildList {
            add(ExternalPlayerApp("current-internal", currentLabel))
            if (activeEngine != null) add(ExternalPlayerApp("other-internal", if (other == AndroidPlaybackEngine.Libmpv) "libmpv (β)" else other.label))
            addAll(available.map { player ->
                if (request.incompatibleWithVlc(player.id))
                    player.copy(name = "${player.name} — $warning")
                else player
            })
        }
        ExternalPlayerSelectionDialog(
            players = choices,
            title = stringResource(Res.string.cd_switch_player_engine),
            selectedPlayerId = "current-internal",
            onDismiss = { pendingExternal = null },
            onPlayerSelected = select@{ chosenId ->
                pendingExternal = null
                if (chosenId == "current-internal") return@select
                if (chosenId == "other-internal") {
                    switchEngine?.invoke()
                    return@select
                }
                if (request.incompatibleWithVlc(chosenId)) {
                    KovelyxToastController.show(warning)
                    return@select
                }
                val playerLaunch = PlayerLaunch(
                    profileId = launch.profileId,
                    title = launch.title,
                    sourceUrl = request.sourceUrl,
                    sourceHeaders = request.sourceHeaders,
                    logo = launch.logo,
                    poster = launch.poster,
                    background = launch.background,
                    seasonNumber = launch.seasonNumber,
                    episodeNumber = launch.episodeNumber,
                    episodeTitle = launch.episodeTitle,
                    episodeThumbnail = launch.episodeThumbnail,
                    streamTitle = request.streamTitle ?: launch.streamTitle,
                    streamSubtitle = launch.streamSubtitle,
                    bingeGroup = launch.bingeGroup,
                    pauseDescription = launch.pauseDescription,
                    providerName = launch.providerName,
                    providerAddonId = launch.providerAddonId,
                    contentType = launch.contentType,
                    videoId = launch.videoId,
                    parentMetaId = launch.parentMetaId,
                    parentMetaType = launch.parentMetaType,
                    initialPositionMs = request.resumePositionMs,
                )
                onExternalPlayerLaunch(playerLaunch)
                val intentResult = ExternalPlayerPlatform.buildIntent(
                    request = request,
                    playerId = chosenId,
                )
                when (intentResult) {
                    is ExternalPlayerIntentResult.Success -> {
                        val launched = launchExternalPlayer(intentResult)
                        if (!launched) {
                            KovelyxToastController.show(externalPlayerFailedText)
                        } else if (chosenId == "infuse") {
                            onBack()
                        }
                    }
                    ExternalPlayerIntentResult.NotConfigured -> {
                        KovelyxToastController.show(externalPlayerNotConfiguredText)
                    }
                    ExternalPlayerIntentResult.Failed -> {
                        KovelyxToastController.show(externalPlayerFailedText)
                    }
                }
            },
        )
    }
}
