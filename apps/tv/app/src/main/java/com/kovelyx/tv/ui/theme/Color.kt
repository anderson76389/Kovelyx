package com.kovelyx.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Immutable
data class KovelyxSurfaceColors(
    val background: Color,
    val raised: Color,
    val card: Color,
    val default: Color,
    val variant: Color,
    val panel: Color,
    val overlay: Color,
    val field: Color,
    val menu: Color,
    val modal: Color,
    val playerOverlay: Color,
    val divider: Color
)

@Immutable
data class KovelyxTextColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val disabled: Color,
    val inverse: Color,
    val onAccent: Color,
    val onOverlay: Color,
    val metadata: Color
)

@Immutable
data class KovelyxFocusColors(
    val ring: Color,
    val background: Color,
    val content: Color,
    val accent: Color,
    val scrim: Color
)

@Immutable
data class KovelyxSelectionColors(
    val background: Color,
    val foreground: Color,
    val border: Color,
    val mutedBackground: Color,
    val mutedForeground: Color
)

@Immutable
data class KovelyxMediaColors(
    val heroScrim: Color,
    val imageScrim: Color,
    val posterFallback: Color,
    val videoControlsScrim: Color,
    val glassPanelTop: Color,
    val glassPanelMiddle: Color,
    val glassPanelBottom: Color,
    val glow: Color
)

@Immutable
data class KovelyxStatusColors(
    val rating: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val info: Color,
    val watched: Color,
    val unwatched: Color,
    val cached: Color,
    val torrent: Color,
    val premium: Color
)

@Immutable
data class KovelyxDisabledColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val overlay: Color
)

@Immutable
data class KovelyxSourceColors(
    val trakt: Color,
    val tmdb: Color,
    val imdb: Color,
    val mdblist: Color
)

@Immutable
data class KovelyxContrastPair(
    val foreground: Color,
    val background: Color
)

class KovelyxColorScheme(
    palette: ThemeColorPalette,
    amoledMode: Boolean = false,
    amoledSurfacesMode: Boolean = false
) {
    private val pureBlack = KovelyxPrimitives.black
    private val pureBlackSurfaces = amoledMode && amoledSurfacesMode

    val Background = if (amoledMode) pureBlack else palette.background
    val BackgroundElevated = if (pureBlackSurfaces) pureBlack else palette.backgroundElevated
    val BackgroundCard = if (pureBlackSurfaces) pureBlack else palette.backgroundCard
    val Surface = if (pureBlackSurfaces) pureBlack else palette.surface
    val SurfaceVariant = if (pureBlackSurfaces) pureBlack else palette.surfaceVariant
    val Panel = if (pureBlackSurfaces) pureBlack else palette.panel
    val Overlay = palette.overlay
    val Field = if (pureBlackSurfaces) pureBlack else palette.field
    val Menu = if (pureBlackSurfaces) pureBlack else palette.menu
    val Modal = if (pureBlackSurfaces) pureBlack else palette.modal
    val PlayerOverlay = palette.playerOverlay
    val Divider = KovelyxPrimitives.neutral750

    val Primary = KovelyxPrimitives.neutral500
    val PrimaryVariant = KovelyxPrimitives.neutral650
    val OnPrimary = KovelyxPrimitives.white
    val Secondary = palette.secondary
    val SecondaryVariant = palette.secondaryVariant
    val OnSecondary = palette.onSecondary
    val OnSecondaryVariant = palette.onSecondaryVariant

    val TextPrimary = KovelyxPrimitives.white
    val TextSecondary = KovelyxPrimitives.neutral400
    val TextTertiary = KovelyxPrimitives.neutral600
    val TextDisabled = KovelyxPrimitives.neutral700
    val TextInverse = KovelyxPrimitives.neutral925

    val FocusRing = palette.focusRing
    val FocusBackground = palette.focusBackground
    val FocusContent = KovelyxPrimitives.white
    val FocusScrim = KovelyxPrimitives.black.copy(alpha = 0.32f)

    val Rating = KovelyxPrimitives.rating
    val Error = KovelyxPrimitives.error
    val Warning = KovelyxPrimitives.warning
    val Success = KovelyxPrimitives.success
    val Info = KovelyxPrimitives.info
    val Watched = KovelyxPrimitives.success
    val Unwatched = KovelyxPrimitives.neutral600
    val Cached = KovelyxPrimitives.blue300
    val Torrent = KovelyxPrimitives.torrent
    val Premium = KovelyxPrimitives.premium

    val Border = KovelyxPrimitives.neutral750
    val BorderFocused = FocusRing
    val BorderMuted = KovelyxPrimitives.neutral750.copy(alpha = 0.58f)

    val Scrim = KovelyxPrimitives.black.copy(alpha = 0.62f)
    val ImageScrim = KovelyxPrimitives.black.copy(alpha = 0.58f)
    val VideoControlsScrim = KovelyxPrimitives.black.copy(alpha = 0.72f)
    val PosterFallback = BackgroundCard

    val DisabledContainer = SurfaceVariant.copy(alpha = 0.42f)
    val DisabledContent = TextDisabled
    val DisabledBorder = Border.copy(alpha = 0.48f)
    val DisabledOverlay = KovelyxPrimitives.black.copy(alpha = 0.42f)

    val surfaces = KovelyxSurfaceColors(
        background = Background,
        raised = BackgroundElevated,
        card = BackgroundCard,
        default = Surface,
        variant = SurfaceVariant,
        panel = Panel,
        overlay = Overlay,
        field = Field,
        menu = Menu,
        modal = Modal,
        playerOverlay = PlayerOverlay,
        divider = Divider
    )

    val text = KovelyxTextColors(
        primary = TextPrimary,
        secondary = TextSecondary,
        tertiary = TextTertiary,
        disabled = TextDisabled,
        inverse = TextInverse,
        onAccent = OnSecondary,
        onOverlay = KovelyxPrimitives.white,
        metadata = TextSecondary
    )

    val focus = KovelyxFocusColors(
        ring = FocusRing,
        background = FocusBackground,
        content = FocusContent,
        accent = Secondary,
        scrim = FocusScrim
    )

    val selection = KovelyxSelectionColors(
        background = Secondary,
        foreground = OnSecondary,
        border = SecondaryVariant,
        mutedBackground = FocusBackground,
        mutedForeground = TextPrimary
    )

    val media = KovelyxMediaColors(
        heroScrim = Scrim,
        imageScrim = ImageScrim,
        posterFallback = PosterFallback,
        videoControlsScrim = VideoControlsScrim,
        glassPanelTop = Color(0xD64A4F59),
        glassPanelMiddle = Color(0xCC3F454F),
        glassPanelBottom = Color(0xC640474F),
        glow = FocusRing.copy(alpha = 0.32f)
    )

    val status = KovelyxStatusColors(
        rating = Rating,
        error = Error,
        warning = Warning,
        success = Success,
        info = Info,
        watched = Watched,
        unwatched = Unwatched,
        cached = Cached,
        torrent = Torrent,
        premium = Premium
    )

    val disabled = KovelyxDisabledColors(
        container = DisabledContainer,
        content = DisabledContent,
        border = DisabledBorder,
        overlay = DisabledOverlay
    )

    val source = KovelyxSourceColors(
        trakt = KovelyxPrimitives.trakt,
        tmdb = KovelyxPrimitives.tmdb,
        imdb = KovelyxPrimitives.imdb,
        mdblist = KovelyxPrimitives.mdblist
    )

    val contrastPairs = listOf(
        KovelyxContrastPair(TextPrimary, Background),
        KovelyxContrastPair(TextPrimary, BackgroundCard),
        KovelyxContrastPair(TextSecondary, Background),
        KovelyxContrastPair(OnSecondary, Secondary),
        KovelyxContrastPair(FocusContent, FocusBackground),
        KovelyxContrastPair(KovelyxPrimitives.white, PlayerOverlay)
    )
}

object KovelyxColors {
    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.Background

    val BackgroundElevated: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.BackgroundElevated

    val BackgroundCard: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.BackgroundCard

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.Surface

    val SurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.SurfaceVariant

    val Primary = KovelyxPrimitives.neutral500
    val PrimaryVariant = KovelyxPrimitives.neutral650
    val OnPrimary = KovelyxPrimitives.white
    val TextPrimary = KovelyxPrimitives.white
    val TextSecondary = KovelyxPrimitives.neutral400
    val TextTertiary = KovelyxPrimitives.neutral600
    val TextDisabled = KovelyxPrimitives.neutral700
    val Rating = KovelyxPrimitives.rating
    val Error = KovelyxPrimitives.error
    val Success = KovelyxPrimitives.success
    val Warning = KovelyxPrimitives.warning
    val Info = KovelyxPrimitives.info
    val Border = KovelyxPrimitives.neutral750

    val Secondary: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.Secondary

    val SecondaryVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.SecondaryVariant

    val OnSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.OnSecondary

    val OnSecondaryVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.OnSecondaryVariant

    val FocusRing: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.FocusRing

    val FocusBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.FocusBackground

    val BorderFocused: Color
        @Composable
        @ReadOnlyComposable
        get() = KovelyxTheme.colors.BorderFocused
}
