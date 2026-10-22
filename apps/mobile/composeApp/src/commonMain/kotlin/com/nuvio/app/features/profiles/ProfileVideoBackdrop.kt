package com.nuvio.app.features.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal fun isProfileVideo(url: String?): Boolean =
    url?.substringBefore('?')?.lowercase()?.let {
        it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov")
    } == true

/** Fond vidéo privé, silencieux et relancé en boucle sur Android. */
@Composable
internal expect fun ProfileVideoBackdrop(url: String, modifier: Modifier = Modifier)
