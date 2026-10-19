package com.nuvio.app.features.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

@Composable
internal actual fun ProfileVideoBackdrop(url: String, modifier: Modifier) {
    // Utilise le moteur multiplateforme déjà inclus dans le dépôt, plutôt
    // que lancer un exécutable externe ou dépendre de Nuvio.
    val player=rememberVideoPlayerState()
    LaunchedEffect(url,player) {
        player.volume=0f
        player.loop=true
        player.openUri(url)
    }
    VideoPlayerSurface(
        playerState=player,
        modifier=modifier,
        contentScale=ContentScale.Crop,
    )
}
