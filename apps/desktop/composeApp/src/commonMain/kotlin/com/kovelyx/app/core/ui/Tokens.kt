package com.kovelyx.app.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object KovelyxTokens {
    object Space {
        val none = 0.dp
        val hairline = 0.5.dp
        val s1 = 1.dp
        val s2 = 2.dp
        val s3 = 3.dp
        val s4 = 4.dp
        val s5 = 5.dp
        val s6 = 6.dp
        val s7 = 7.dp
        val s8 = 8.dp
        val s10 = 10.dp
        val s12 = 12.dp
        val s14 = 14.dp
        val s16 = 16.dp
        val s18 = 18.dp
        val s20 = 20.dp
        val s22 = 22.dp
        val s24 = 24.dp
        val s28 = 28.dp
        val s32 = 32.dp
        val s36 = 36.dp
        val s40 = 40.dp
        val s48 = 48.dp
        val s56 = 56.dp
        val s64 = 64.dp
        val s72 = 72.dp
        val s80 = 80.dp
        val s96 = 96.dp
    }

    object Radius {
        val none = Space.none
        val xs = Space.s4
        val sm = Space.s6
        val md = Space.s8
        val lg = Space.s12
        val xl = Space.s16
        val xxl = Space.s24
        val full = 999.dp

        val card = xxl
        val compactCard = lg
        val sheet = xxl
        val dialog = Space.s28
        val button = xl
        val chip = full
        val poster = lg
        val avatar = full
        val playerPanel = xxl
    }

    object Border {
        val hairline = Space.hairline
        val thin = Space.s1
        val medium = Space.s2
    }

    object Elevation {
        val none = Space.none
        val raised = Space.s2
        val modal = Space.s8
        val overlay = Space.s12
        val playerControls = Space.s4
    }

    object Opacity {
        const val invisible = 0f
        const val disabled = 0.38f
        const val secondary = 0.70f
        const val muted = 0.60f
        const val selected = 0.15f
        const val hover = 0.08f
        const val pressed = 0.12f
        const val subtle = 0.06f
        const val medium = 0.52f
        const val strong = 0.75f
        const val overlayLight = 0.35f
        const val overlayMedium = 0.56f
        const val overlayHeavy = 0.82f
        const val visible = 1f
    }

    object Motion {
        const val instantMillis = 0
        const val fastMillis = 150
        const val normalMillis = 220
        const val sheetEnterMillis = 300
        const val sheetExitMillis = 250
        const val slowMillis = 400
        const val cinematicMillis = 700

        val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
        val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    }

    object Icon {
        val xs = Space.s12
        val sm = Space.s16
        val md = Space.s20
        val lg = Space.s24
        val xl = Space.s32
        val xxl = Space.s40
    }

    object Type {
        val labelXs = 11.sp
        val labelSm = 12.sp
        val bodySm = 13.sp
        val bodyMd = 14.sp
        val bodyApp = 15.sp
        val bodyLg = 16.sp
        val titleSm = 18.sp
        val headline = 26.sp
        val titleMd = 22.sp
        val titleLg = 28.sp
        val displaySm = 32.sp
        val pageDisplay = 38.sp
        val displayMd = 48.sp
    }

    object LineHeight {
        val labelXs = 14.sp
        val labelSm = 15.sp
        val bodySm = 18.sp
        val bodyMd = 20.sp
        val bodyApp = 22.sp
        val bodyLg = 22.sp
        val titleSm = 22.sp
        val materialTitleLarge = 24.sp
        val headline = 30.sp
        val titleMd = 26.sp
        val titleLg = 32.sp
        val displaySm = 36.sp
        val pageDisplay = 42.sp
        val displayMd = 52.sp
    }

    object LetterSpacing {
        val none = 0.sp
        val pageDisplay = (-1.2).sp
        val headline = (-0.8).sp
        val label = 0.8.sp
    }

    object Breakpoint {
        val phone = 0.dp
        val largePhone = 420.dp
        val tablet = 600.dp
        val largeTablet = 840.dp
        val desktop = 1024.dp
        val playerWide = 1280.dp
    }

    object Z {
        const val base = 0f
        const val stickyHeader = 2f
        const val navigation = 4f
        const val sheet = 8f
        const val dialog = 10f
        const val playerOverlay = 12f
        const val toast = 16f
    }
}

@Immutable
data class KovelyxColorTokens(
    val background: Color,
    val backgroundAmoled: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val surfaceSheet: Color,
    val surfaceDialog: Color,
    val surfacePopover: Color,
    val nativeChrome: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val textInverse: Color,
    val accent: Color,
    val accentStrong: Color,
    val onAccent: Color,
    val focusRing: Color,
    val focusBackground: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    val borderFocus: Color,
    val borderSelected: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val neutral: Color,
    val overlayScrim: Color,
    val overlayHover: Color,
    val overlayPressed: Color,
    val overlaySelected: Color,
    val overlayDisabled: Color,
    val shimmer: Color,
    val skeleton: Color,
    val playerControlsBackground: Color,
    val playerControlsForeground: Color,
    val playerTimelineTrack: Color,
    val playerTimelineFill: Color,
    val playerSubtitlePreview: Color,
    val playerBuffering: Color,
    val parentalGuide: Color,
)

@Immutable
data class KovelyxShapeTokens(
    val card: Shape,
    val compactCard: Shape,
    val sheet: Shape,
    val dialog: Shape,
    val button: Shape,
    val chip: Shape,
    val poster: Shape,
    val avatar: Shape,
    val playerPanel: Shape,
)

@Immutable
data class KovelyxSpacingTokens(
    val screenHorizontal: Dp,
    val screenTop: Dp,
    val screenBottom: Dp,
    val sectionGap: Dp,
    val listGap: Dp,
    val railGap: Dp,
    val cardPadding: Dp,
    val cardPaddingCompact: Dp,
    val controlGap: Dp,
    val dialogPadding: Dp,
    val sheetPadding: Dp,
    val playerOverlayPadding: Dp,
)

@Immutable
data class KovelyxBorderTokens(
    val hairline: Dp,
    val thin: Dp,
    val medium: Dp,
)

@Immutable
data class KovelyxElevationTokens(
    val flat: Dp,
    val raised: Dp,
    val modal: Dp,
    val overlay: Dp,
    val playerControls: Dp,
)

@Immutable
data class KovelyxMotionTokens(
    val instantMillis: Int,
    val fastMillis: Int,
    val normalMillis: Int,
    val sheetEnterMillis: Int,
    val sheetExitMillis: Int,
    val slowMillis: Int,
    val cinematicMillis: Int,
    val standard: Easing,
    val emphasized: Easing,
    val decelerate: Easing,
    val accelerate: Easing,
)

@Immutable
data class KovelyxOpacityTokens(
    val invisible: Float,
    val disabled: Float,
    val secondary: Float,
    val muted: Float,
    val selected: Float,
    val hover: Float,
    val pressed: Float,
    val subtle: Float,
    val medium: Float,
    val strong: Float,
    val overlayLight: Float,
    val overlayMedium: Float,
    val overlayHeavy: Float,
    val visible: Float,
)

@Immutable
data class KovelyxIconTokens(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
)

@Immutable
data class KovelyxBreakpointTokens(
    val phone: Dp,
    val largePhone: Dp,
    val tablet: Dp,
    val largeTablet: Dp,
    val desktop: Dp,
    val playerWide: Dp,
)

@Immutable
data class KovelyxComponentTokens(
    val navItemShape: Shape,
    val navIconSize: Dp,
    val navItemMaxWidth: Dp,
    val sheetMaxWidth: Dp,
    val dialogMaxWidth: Dp,
    val chipHorizontalPadding: Dp,
    val chipVerticalPadding: Dp,
    val posterRadius: Dp,
    val avatarSize: Dp,
    val playerPanelMaxWidth: Dp,
)

@Immutable
data class KovelyxThemeTokens(
    val colors: KovelyxColorTokens,
    val spacing: KovelyxSpacingTokens,
    val shapes: KovelyxShapeTokens,
    val borders: KovelyxBorderTokens,
    val elevation: KovelyxElevationTokens,
    val motion: KovelyxMotionTokens,
    val opacity: KovelyxOpacityTokens,
    val icons: KovelyxIconTokens,
    val breakpoints: KovelyxBreakpointTokens,
    val components: KovelyxComponentTokens,
)

internal val LocalKovelyxThemeTokens = staticCompositionLocalOf {
    defaultKovelyxThemeTokens(ThemeColors.White, amoled = false, colorScheme = null)
}

val MaterialTheme.nuvio: KovelyxThemeTokens
    @Composable
    @Stable
    get() = LocalKovelyxThemeTokens.current

internal fun defaultKovelyxThemeTokens(
    palette: ThemeColorPalette,
    amoled: Boolean,
    colorScheme: ColorScheme?,
): KovelyxThemeTokens {
    val background = if (amoled) Color.Black else palette.background
    val textPrimary = Color(0xFFF5F7F8)
    val textSecondary = Color(0xFFB8BEC5)
    val textMuted = Color(0xFF969CA3)
    val surface = palette.backgroundElevated
    val surfaceCard = palette.backgroundCard
    val accent = palette.secondary
    val accentStrong = palette.secondaryVariant
    val borderSubtle = Color(0xFF252A2A).copy(alpha = 0.55f)
    val borderDefault = Color(0xFF252A2A)
    val overlayScrim = Color.Black.copy(alpha = KovelyxTokens.Opacity.overlayMedium)

    return KovelyxThemeTokens(
        colors = KovelyxColorTokens(
            background = background,
            backgroundAmoled = Color.Black,
            surface = surface,
            surfaceElevated = surface,
            surfaceCard = surfaceCard,
            surfaceSheet = surface,
            surfaceDialog = surface,
            surfacePopover = surfaceCard,
            nativeChrome = background,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            textDisabled = textMuted.copy(alpha = KovelyxTokens.Opacity.disabled),
            textInverse = Color(0xFF111111),
            accent = accent,
            accentStrong = accentStrong,
            onAccent = palette.onSecondary,
            focusRing = palette.focusRing,
            focusBackground = palette.focusBackground,
            borderSubtle = borderSubtle,
            borderDefault = borderDefault,
            borderStrong = Color(0xFF3A4040),
            borderFocus = palette.focusRing,
            borderSelected = accent.copy(alpha = KovelyxTokens.Opacity.strong),
            success = Color(0xFF66BB6A),
            warning = Color(0xFFFFC857),
            danger = colorScheme?.error ?: Color(0xFFE36A8A),
            info = Color(0xFF42A5F5),
            neutral = textMuted,
            overlayScrim = overlayScrim,
            overlayHover = Color.White.copy(alpha = KovelyxTokens.Opacity.hover),
            overlayPressed = Color.White.copy(alpha = KovelyxTokens.Opacity.pressed),
            overlaySelected = Color.White.copy(alpha = KovelyxTokens.Opacity.selected),
            overlayDisabled = Color.Black.copy(alpha = KovelyxTokens.Opacity.disabled),
            shimmer = Color.White.copy(alpha = 0.10f),
            skeleton = Color.White.copy(alpha = 0.06f),
            playerControlsBackground = Color.Black.copy(alpha = 0.72f),
            playerControlsForeground = Color.White,
            playerTimelineTrack = Color.White.copy(alpha = 0.30f),
            playerTimelineFill = accent,
            playerSubtitlePreview = Color.Black.copy(alpha = 0.55f),
            playerBuffering = accent,
            parentalGuide = Color(0xFF5D1F1F),
        ),
        spacing = KovelyxSpacingTokens(
            screenHorizontal = KovelyxTokens.Space.s16,
            screenTop = KovelyxTokens.Space.s10,
            screenBottom = KovelyxTokens.Space.s18,
            sectionGap = KovelyxTokens.Space.s24,
            listGap = KovelyxTokens.Space.s12,
            railGap = KovelyxTokens.Space.s14,
            cardPadding = KovelyxTokens.Space.s18,
            cardPaddingCompact = KovelyxTokens.Space.s14,
            controlGap = KovelyxTokens.Space.s8,
            dialogPadding = KovelyxTokens.Space.s20,
            sheetPadding = KovelyxTokens.Space.s20,
            playerOverlayPadding = KovelyxTokens.Space.s24,
        ),
        shapes = KovelyxShapeTokens(
            card = RoundedCornerShape(KovelyxTokens.Radius.card),
            compactCard = RoundedCornerShape(KovelyxTokens.Radius.compactCard),
            sheet = RoundedCornerShape(KovelyxTokens.Radius.sheet),
            dialog = RoundedCornerShape(KovelyxTokens.Radius.dialog),
            button = RoundedCornerShape(KovelyxTokens.Radius.button),
            chip = RoundedCornerShape(KovelyxTokens.Radius.chip),
            poster = RoundedCornerShape(KovelyxTokens.Radius.poster),
            avatar = RoundedCornerShape(KovelyxTokens.Radius.avatar),
            playerPanel = RoundedCornerShape(KovelyxTokens.Radius.playerPanel),
        ),
        borders = KovelyxBorderTokens(
            hairline = KovelyxTokens.Border.hairline,
            thin = KovelyxTokens.Border.thin,
            medium = KovelyxTokens.Border.medium,
        ),
        elevation = KovelyxElevationTokens(
            flat = KovelyxTokens.Elevation.none,
            raised = KovelyxTokens.Elevation.raised,
            modal = KovelyxTokens.Elevation.modal,
            overlay = KovelyxTokens.Elevation.overlay,
            playerControls = KovelyxTokens.Elevation.playerControls,
        ),
        motion = KovelyxMotionTokens(
            instantMillis = KovelyxTokens.Motion.instantMillis,
            fastMillis = KovelyxTokens.Motion.fastMillis,
            normalMillis = KovelyxTokens.Motion.normalMillis,
            sheetEnterMillis = KovelyxTokens.Motion.sheetEnterMillis,
            sheetExitMillis = KovelyxTokens.Motion.sheetExitMillis,
            slowMillis = KovelyxTokens.Motion.slowMillis,
            cinematicMillis = KovelyxTokens.Motion.cinematicMillis,
            standard = KovelyxTokens.Motion.standard,
            emphasized = KovelyxTokens.Motion.emphasized,
            decelerate = KovelyxTokens.Motion.decelerate,
            accelerate = KovelyxTokens.Motion.accelerate,
        ),
        opacity = KovelyxOpacityTokens(
            invisible = KovelyxTokens.Opacity.invisible,
            disabled = KovelyxTokens.Opacity.disabled,
            secondary = KovelyxTokens.Opacity.secondary,
            muted = KovelyxTokens.Opacity.muted,
            selected = KovelyxTokens.Opacity.selected,
            hover = KovelyxTokens.Opacity.hover,
            pressed = KovelyxTokens.Opacity.pressed,
            subtle = KovelyxTokens.Opacity.subtle,
            medium = KovelyxTokens.Opacity.medium,
            strong = KovelyxTokens.Opacity.strong,
            overlayLight = KovelyxTokens.Opacity.overlayLight,
            overlayMedium = KovelyxTokens.Opacity.overlayMedium,
            overlayHeavy = KovelyxTokens.Opacity.overlayHeavy,
            visible = KovelyxTokens.Opacity.visible,
        ),
        icons = KovelyxIconTokens(
            xs = KovelyxTokens.Icon.xs,
            sm = KovelyxTokens.Icon.sm,
            md = KovelyxTokens.Icon.md,
            lg = KovelyxTokens.Icon.lg,
            xl = KovelyxTokens.Icon.xl,
            xxl = KovelyxTokens.Icon.xxl,
        ),
        breakpoints = KovelyxBreakpointTokens(
            phone = KovelyxTokens.Breakpoint.phone,
            largePhone = KovelyxTokens.Breakpoint.largePhone,
            tablet = KovelyxTokens.Breakpoint.tablet,
            largeTablet = KovelyxTokens.Breakpoint.largeTablet,
            desktop = KovelyxTokens.Breakpoint.desktop,
            playerWide = KovelyxTokens.Breakpoint.playerWide,
        ),
        components = KovelyxComponentTokens(
            navItemShape = RoundedCornerShape(KovelyxTokens.Radius.xl),
            navIconSize = KovelyxTokens.Icon.xl,
            navItemMaxWidth = 150.dp,
            sheetMaxWidth = 520.dp,
            dialogMaxWidth = 460.dp,
            chipHorizontalPadding = KovelyxTokens.Space.s14,
            chipVerticalPadding = KovelyxTokens.Space.s8,
            posterRadius = KovelyxTokens.Radius.poster,
            avatarSize = KovelyxTokens.Space.s48,
            playerPanelMaxWidth = 600.dp,
        ),
    )
}
