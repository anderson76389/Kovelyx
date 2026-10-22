package com.nuvio.app.features.profiles

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier

@Composable
internal actual fun ProfileVideoBackdrop(url: String, modifier: Modifier) {
    // Ne pas tenter de démarrer un lecteur natif non initialisé.
    // Le fichier est néanmoins conservé et synchronisable vers Android/TV.
    Box(modifier = modifier)
}
