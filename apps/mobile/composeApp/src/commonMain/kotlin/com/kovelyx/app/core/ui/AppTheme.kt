package com.kovelyx.app.core.ui

import kovelyx.composeapp.generated.resources.Res
import kovelyx.composeapp.generated.resources.theme_amber
import kovelyx.composeapp.generated.resources.theme_crimson
import kovelyx.composeapp.generated.resources.theme_emerald
import kovelyx.composeapp.generated.resources.theme_ocean
import kovelyx.composeapp.generated.resources.theme_rose
import kovelyx.composeapp.generated.resources.theme_violet
import kovelyx.composeapp.generated.resources.theme_white
import kovelyx.composeapp.generated.resources.theme_gold
import kovelyx.composeapp.generated.resources.theme_jade
import kovelyx.composeapp.generated.resources.theme_rose_gold
import kovelyx.composeapp.generated.resources.theme_arctic_blue
import kovelyx.composeapp.generated.resources.theme_graphite
import kovelyx.composeapp.generated.resources.theme_custom
import org.jetbrains.compose.resources.StringResource

enum class AppTheme {
    GOLD,
    JADE,
    ROSE_GOLD,
    ARCTIC_BLUE,
    GRAPHITE,
    CUSTOM,
    CRIMSON,
    OCEAN,
    VIOLET,
    EMERALD,
    AMBER,
    ROSE,
    WHITE,
}

val AppTheme.labelRes: StringResource
    get() = when (this) {
        AppTheme.GOLD -> Res.string.theme_gold
        AppTheme.JADE -> Res.string.theme_jade
        AppTheme.ROSE_GOLD -> Res.string.theme_rose_gold
        AppTheme.ARCTIC_BLUE -> Res.string.theme_arctic_blue
        AppTheme.GRAPHITE -> Res.string.theme_graphite
        AppTheme.CUSTOM -> Res.string.theme_custom
        AppTheme.CRIMSON -> Res.string.theme_crimson
        AppTheme.OCEAN -> Res.string.theme_ocean
        AppTheme.VIOLET -> Res.string.theme_violet
        AppTheme.EMERALD -> Res.string.theme_emerald
        AppTheme.AMBER -> Res.string.theme_amber
        AppTheme.ROSE -> Res.string.theme_rose
        AppTheme.WHITE -> Res.string.theme_white
    }
