package com.kovelyx.tv.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class KovelyxRadiusTokens(
    val none: Dp,
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val panel: Dp,
    val full: Dp
)

@Immutable
data class KovelyxShapeTokens(
    val posterCard: Shape,
    val backdropCard: Shape,
    val collectionCard: Shape,
    val button: Shape,
    val iconButton: Shape,
    val chip: Shape,
    val badge: Shape,
    val dialog: Shape,
    val sidePanel: Shape,
    val sidebar: Shape,
    val navItem: Shape,
    val progress: Shape,
    val slider: Shape,
    val field: Shape,
    val menu: Shape,
    val circle: Shape
)

object KovelyxRadii {
    val tokens = KovelyxRadiusTokens(
        none = 0.dp,
        xxs = 2.dp,
        xs = 4.dp,
        sm = 8.dp,
        md = 12.dp,
        lg = 14.dp,
        xl = 16.dp,
        xxl = 20.dp,
        panel = 28.dp,
        full = 999.dp
    )
}

object KovelyxShapes {
    val tokens = KovelyxShapeTokens(
        posterCard = RoundedCornerShape(KovelyxRadii.tokens.md),
        backdropCard = RoundedCornerShape(KovelyxRadii.tokens.xl),
        collectionCard = RoundedCornerShape(KovelyxRadii.tokens.xl),
        button = RoundedCornerShape(KovelyxRadii.tokens.md),
        iconButton = RoundedCornerShape(KovelyxRadii.tokens.md),
        chip = RoundedCornerShape(KovelyxRadii.tokens.full),
        badge = RoundedCornerShape(KovelyxRadii.tokens.xs),
        dialog = RoundedCornerShape(KovelyxRadii.tokens.xl),
        sidePanel = RoundedCornerShape(KovelyxRadii.tokens.xxl),
        sidebar = RoundedCornerShape(30.dp),
        navItem = RoundedCornerShape(KovelyxRadii.tokens.full),
        progress = RoundedCornerShape(KovelyxRadii.tokens.xxs),
        slider = RoundedCornerShape(KovelyxRadii.tokens.full),
        field = RoundedCornerShape(KovelyxRadii.tokens.md),
        menu = RoundedCornerShape(KovelyxRadii.tokens.lg),
        circle = CircleShape
    )
}
