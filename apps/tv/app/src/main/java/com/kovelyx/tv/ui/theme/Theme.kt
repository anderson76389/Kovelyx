package com.kovelyx.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.kovelyx.tv.domain.model.AppFont
import com.kovelyx.tv.domain.model.AppTheme
import com.kovelyx.tv.domain.model.CustomThemeColors
import com.kovelyx.tv.domain.model.SettingsUiStyle

data class KovelyxExtendedColors(
    val backgroundElevated: Color,
    val backgroundCard: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val focusRing: Color,
    val focusBackground: Color,
    val rating: Color
)

val LocalKovelyxColors = staticCompositionLocalOf {
    KovelyxColorScheme(ThemeColors.Ocean)
}

val LocalKovelyxExtendedColors = staticCompositionLocalOf {
    KovelyxExtendedColors(
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF242424),
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF808080),
        focusRing = ThemeColors.Ocean.focusRing,
        focusBackground = ThemeColors.Ocean.focusBackground,
        rating = Color(0xFFFFD700)
    )
}

val LocalKovelyxTextStyles = staticCompositionLocalOf { KovelyxTextStyles }

val LocalAppTheme = staticCompositionLocalOf { AppTheme.WHITE }

val LocalThemePalette = staticCompositionLocalOf { ThemeColors.White }

val LocalSettingsUiStyle = staticCompositionLocalOf { SettingsUiStyle.CLASSIC }

val LocalKovelyxFocusRingStyle = staticCompositionLocalOf {
    createFocusRingStyle(ThemeColors.Ocean)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KovelyxTheme(
    appTheme: AppTheme = AppTheme.WHITE,
    appFont: AppFont = AppFont.INTER,
    amoledMode: Boolean = false,
    amoledSurfacesMode: Boolean = false,
    settingsUiStyle: SettingsUiStyle = SettingsUiStyle.CLASSIC,
    customThemeColors: CustomThemeColors = CustomThemeColors.Default,
    content: @Composable () -> Unit
) {
    val palette = androidx.compose.runtime.remember(appTheme, customThemeColors) {
        ThemeColors.getColorPalette(appTheme, customThemeColors)
    }
    val focusRingStyle = createFocusRingStyle(palette)
    val colorScheme = KovelyxColorScheme(
        palette = palette,
        amoledMode = amoledMode,
        amoledSurfacesMode = amoledSurfacesMode
    )
    val typography = buildKovelyxTypography(getFontFamily(appFont))
    val textStyles = buildKovelyxTextStyles(typography)

    val materialColorScheme = darkColorScheme(
        primary = colorScheme.Primary,
        onPrimary = colorScheme.OnPrimary,
        secondary = colorScheme.Secondary,
        onSecondary = colorScheme.OnSecondary,
        background = colorScheme.Background,
        surface = colorScheme.Surface,
        surfaceVariant = colorScheme.SurfaceVariant,
        onBackground = colorScheme.TextPrimary,
        onSurface = colorScheme.TextPrimary,
        onSurfaceVariant = colorScheme.TextSecondary,
        error = colorScheme.Error
    )

    val extendedColors = KovelyxExtendedColors(
        backgroundElevated = colorScheme.BackgroundElevated,
        backgroundCard = colorScheme.BackgroundCard,
        textSecondary = colorScheme.TextSecondary,
        textTertiary = colorScheme.TextTertiary,
        focusRing = colorScheme.FocusRing,
        focusBackground = colorScheme.FocusBackground,
        rating = colorScheme.Rating
    )

    CompositionLocalProvider(
        LocalKovelyxColors provides colorScheme,
        LocalKovelyxExtendedColors provides extendedColors,
        LocalKovelyxTextStyles provides textStyles,
        LocalAppTheme provides appTheme,
        LocalThemePalette provides palette,
        LocalSettingsUiStyle provides settingsUiStyle,
        LocalKovelyxFocusRingStyle provides focusRingStyle
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            content = content
        )
    }
}

object KovelyxTheme {
    val palette: ThemeColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalThemePalette.current

    val colors: KovelyxColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalKovelyxColors.current

    val extendedColors: KovelyxExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKovelyxExtendedColors.current

    val textStyles: KovelyxTextStyleTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalKovelyxTextStyles.current

    val spacing: KovelyxSpacingTokens
        get() = KovelyxSpacing.tokens

    val radii: KovelyxRadiusTokens
        get() = KovelyxRadii.tokens

    val shapes: KovelyxShapeTokens
        get() = KovelyxShapes.tokens

    val sizes: KovelyxSizeTokens
        get() = KovelyxSizes.tokens

    val strokes: KovelyxStrokeTokens
        get() = KovelyxStrokes.tokens

    val elevations: KovelyxElevationTokens
        get() = KovelyxElevations.tokens

    val effects: KovelyxEffectTokens
        get() = KovelyxEffects.tokens

    val motion: KovelyxMotionTokens
        get() = KovelyxMotion.tokens

    val focus: KovelyxFocusTokens
        get() = KovelyxFocus.tokens

    val focusRing: KovelyxFocusRingStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalKovelyxFocusRingStyle.current

    val layout: KovelyxLayoutTokens
        get() = KovelyxLayout.tokens

    val media: KovelyxMediaTokens
        get() = KovelyxMedia.tokens

    val components: KovelyxComponentTokens
        get() = KovelyxComponents.tokens

    val currentTheme: AppTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val settingsUiStyle: SettingsUiStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalSettingsUiStyle.current
}
