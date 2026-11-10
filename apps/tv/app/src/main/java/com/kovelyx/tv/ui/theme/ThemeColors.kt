package com.kovelyx.tv.ui.theme

import androidx.compose.ui.graphics.Color
import com.kovelyx.tv.domain.model.AppTheme
import com.kovelyx.tv.domain.model.CustomThemeColors

data class ThemeColorPalette(
    val secondary: Color,
    val secondaryVariant: Color,
    val onSecondary: Color = KovelyxPrimitives.white,
    val onSecondaryVariant: Color = KovelyxPrimitives.white,
    val accentGradient: List<Color> = listOf(secondary),
    val focusRing: Color,
    val focusRingGradient: List<Color> = listOf(focusRing),
    val focusBackground: Color,
    val background: Color = KovelyxPrimitives.neutral950,
    val backgroundElevated: Color = KovelyxPrimitives.neutral900,
    val backgroundCard: Color = KovelyxPrimitives.neutral825,
    val surface: Color = KovelyxPrimitives.neutral875,
    val surfaceVariant: Color = KovelyxPrimitives.neutral800,
    val panel: Color = KovelyxPrimitives.neutral900,
    val overlay: Color = Color(0xD9000000),
    val field: Color = KovelyxPrimitives.neutral850,
    val menu: Color = KovelyxPrimitives.neutral875,
    val modal: Color = KovelyxPrimitives.neutral900,
    val playerOverlay: Color = Color(0xCC000000)
)

object ThemeColors {
    val Crimson = ThemeColorPalette(
        secondary = KovelyxPrimitives.red500,
        secondaryVariant = KovelyxPrimitives.red600,
        focusRing = KovelyxPrimitives.red300,
        focusBackground = Color(0xFF3D1A1A),
        backgroundCard = Color(0xFF241A1A)
    )

    val Ocean = ThemeColorPalette(
        secondary = KovelyxPrimitives.blue500,
        secondaryVariant = KovelyxPrimitives.blue700,
        focusRing = KovelyxPrimitives.blue300,
        focusBackground = Color(0xFF1A2D3D),
        background = Color(0xFF0D0D0F),
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1A1F24)
    )

    val Violet = ThemeColorPalette(
        secondary = KovelyxPrimitives.violet500,
        secondaryVariant = KovelyxPrimitives.violet700,
        focusRing = KovelyxPrimitives.violet300,
        focusBackground = Color(0xFF2D1A3D),
        background = Color(0xFF0D0D0F),
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1F1A24)
    )

    val Emerald = ThemeColorPalette(
        secondary = KovelyxPrimitives.green500,
        secondaryVariant = KovelyxPrimitives.green700,
        focusRing = KovelyxPrimitives.green300,
        focusBackground = Color(0xFF1A3D1E),
        backgroundCard = Color(0xFF1A241A)
    )

    val Amber = ThemeColorPalette(
        secondary = KovelyxPrimitives.amber500,
        secondaryVariant = KovelyxPrimitives.amber700,
        focusRing = KovelyxPrimitives.amber300,
        focusBackground = Color(0xFF3D2D1A),
        background = Color(0xFF0F0D0D),
        backgroundElevated = Color(0xFF1E1A1A),
        backgroundCard = Color(0xFF24201A)
    )

    val Rose = ThemeColorPalette(
        secondary = KovelyxPrimitives.rose500,
        secondaryVariant = KovelyxPrimitives.rose700,
        focusRing = KovelyxPrimitives.rose300,
        focusBackground = Color(0xFF3D1A2D),
        backgroundCard = Color(0xFF241A1F)
    )

    val White = ThemeColorPalette(
        secondary = KovelyxPrimitives.neutral100,
        secondaryVariant = KovelyxPrimitives.neutral200,
        onSecondary = KovelyxPrimitives.neutral925,
        onSecondaryVariant = KovelyxPrimitives.neutral925,
        focusRing = KovelyxPrimitives.white,
        focusBackground = Color(0xFF303030),
        backgroundCard = KovelyxPrimitives.neutral850
    )

    fun getColorPalette(
        theme: AppTheme,
        customColors: CustomThemeColors = CustomThemeColors.Default
    ): ThemeColorPalette {
        return when (theme) {
            AppTheme.CUSTOM -> customColors.toColorPalette()
            AppTheme.GOLD -> SupporterThemeColors.Gold
            AppTheme.JADE -> SupporterThemeColors.Jade
            AppTheme.ROSE_GOLD -> SupporterThemeColors.RoseGold
            AppTheme.ARCTIC_BLUE -> SupporterThemeColors.ArcticBlue
            AppTheme.GRAPHITE -> SupporterThemeColors.Graphite
            AppTheme.CRIMSON -> Crimson
            AppTheme.OCEAN -> Ocean
            AppTheme.VIOLET -> Violet
            AppTheme.EMERALD -> Emerald
            AppTheme.AMBER -> Amber
            AppTheme.ROSE -> Rose
            AppTheme.WHITE -> White
        }
    }
}
