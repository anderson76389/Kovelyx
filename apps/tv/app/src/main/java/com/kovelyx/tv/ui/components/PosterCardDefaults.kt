package com.kovelyx.tv.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import com.kovelyx.tv.ui.theme.KovelyxComponents

val LocalLandscapePosterMode = staticCompositionLocalOf { false }
val LocalAlwaysBackdropWithLogo = staticCompositionLocalOf { false }

@Immutable
data class PosterCardStyle(
    val width: Dp = KovelyxComponents.tokens.posterCard.width,
    val height: Dp = KovelyxComponents.tokens.posterCard.height,
    val cornerRadius: Dp = KovelyxComponents.tokens.posterCard.cornerRadius,
    val focusedBorderWidth: Dp = KovelyxComponents.tokens.posterCard.focusedBorderWidth,
    val focusedScale: Float = KovelyxComponents.tokens.posterCard.focusedScale
) {
    val aspectRatio: Float
        get() = width.value / height.value
}

object PosterCardDefaults {
    val Style = PosterCardStyle()
}
