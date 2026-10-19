package com.nuvio.app.features.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.nuvio.app.features.player.PlayerControlsState
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Lecteur local de fonds vidéo utilisant le moteur mpv déjà embarqué sur PC.
 * Sur Windows, :composeMediaPlayer n'est volontairement PAS présent dans les
 * dépendances : le charger ici empêchait tout le projet de compiler.
 * La musique/vidéo principale conserve son volume réglé par l'utilisateur.
 */
@Composable
internal actual fun ProfileVideoBackdrop(url: String, modifier: Modifier) {
    val host = remember { NativePlayerHost() }
    val controller = remember(host) {
        NativePlayerController(host, muteEmbeddedProfileMedia = true).apply {
            // Le fond est décoratif : jamais de WebView de contrôles visible.
            updateControls(PlayerControlsState(
                controlsVisible = false,
                pauseOverlayEnabled = false,
                volumeLevel = 0f,
            ))
        }
    }
    var ready by remember(host) { mutableStateOf(false) }

    DisposableEffect(host, controller) {
        host.onFirstFullSizePaint = { ready = true }
        host.onDisplayableChanged = { displayable ->
            if (!displayable) ready = false
        }
        onDispose {
            host.onFirstFullSizePaint = null
            host.onDisplayableChanged = null
            controller.dispose()
        }
    }

    LaunchedEffect(controller, url, ready) {
        if (!ready) return@LaunchedEffect
        delay(16L) // laisse terminer la création du peer AWT
        controller.attach(
            sourceUrl = url,
            sourceHeaders = emptyMap(),
            playWhenReady = true,
            initialPositionMs = 0L,
            decoderPriority = 0,
            nvidiaRtxSuperResolutionEnabled = false,
            onError = { /* L'import original reste disponible même en cas de codec non lisible. */ },
        )
    }
    LaunchedEffect(controller, ready) {
        if (!ready) return@LaunchedEffect
        while (isActive) {
            delay(400L)
            if (controller.snapshot().isEnded) {
                controller.seekTo(0L)
                controller.play()
            }
        }
    }
    SwingPanel(
        factory = { host },
        modifier = modifier,
    )
}
