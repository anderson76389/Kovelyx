package com.kovelyx.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class KovelyxCardComponentTokens(
    val width: Dp,
    val height: Dp,
    val cornerRadius: Dp,
    val contentPadding: Dp,
    val focusedBorderWidth: Dp,
    val focusedScale: Float
)

@Immutable
data class KovelyxRowComponentTokens(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val itemSpacing: Dp,
    val titleBottomSpacing: Dp
)

@Immutable
data class KovelyxSidebarComponentTokens(
    val legacyCollapsedWidth: Dp,
    val legacyExpandedWidth: Dp,
    val collapsedWidth: Dp,
    val expandedWidth: Dp,
    val itemHeight: Dp,
    val itemWidth: Dp,
    val iconSize: Dp,
    val leadingVisual: Dp,
    val panelRadius: Dp,
    val contentGap: Dp
)

@Immutable
data class KovelyxDialogComponentTokens(
    val maxWidth: Dp,
    val contentPadding: Dp,
    val cornerRadius: Dp,
    val actionSpacing: Dp
)

@Immutable
data class KovelyxPlayerComponentTokens(
    val overlayHorizontalPadding: Dp,
    val overlayVerticalPadding: Dp,
    val controlSize: Dp,
    val sidePanelWidth: Dp,
    val railWidth: Dp,
    val progressHeight: Dp
)

@Immutable
data class KovelyxSettingsComponentTokens(
    val containerRadius: Dp,
    val secondaryCardRadius: Dp,
    val railItemHeight: Dp,
    val workspacePadding: Dp,
    val rowGap: Dp
)

@Immutable
data class KovelyxComponentTokens(
    val posterCard: KovelyxCardComponentTokens,
    val backdropCard: KovelyxCardComponentTokens,
    val collectionCard: KovelyxCardComponentTokens,
    val continueWatchingCard: KovelyxCardComponentTokens,
    val episodeCard: KovelyxCardComponentTokens,
    val row: KovelyxRowComponentTokens,
    val sidebar: KovelyxSidebarComponentTokens,
    val dialog: KovelyxDialogComponentTokens,
    val sidePanel: KovelyxDialogComponentTokens,
    val settings: KovelyxSettingsComponentTokens,
    val player: KovelyxPlayerComponentTokens,
    val buttonHeight: Dp,
    val chipHeight: Dp,
    val badgeHeight: Dp,
    val skeletonCornerRadius: Dp
)

object KovelyxComponents {
    val tokens = KovelyxComponentTokens(
        posterCard = KovelyxCardComponentTokens(
            width = 126.dp,
            height = 189.dp,
            cornerRadius = 12.dp,
            contentPadding = 8.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        backdropCard = KovelyxCardComponentTokens(
            width = 320.dp,
            height = 180.dp,
            cornerRadius = 16.dp,
            contentPadding = 16.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        collectionCard = KovelyxCardComponentTokens(
            width = 320.dp,
            height = 180.dp,
            cornerRadius = 16.dp,
            contentPadding = 16.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        continueWatchingCard = KovelyxCardComponentTokens(
            width = 260.dp,
            height = 146.dp,
            cornerRadius = 12.dp,
            contentPadding = 12.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        episodeCard = KovelyxCardComponentTokens(
            width = 320.dp,
            height = 207.dp,
            cornerRadius = 16.dp,
            contentPadding = 16.dp,
            focusedBorderWidth = 2.dp,
            focusedScale = 1.02f
        ),
        row = KovelyxRowComponentTokens(
            horizontalPadding = 48.dp,
            verticalPadding = 6.dp,
            itemSpacing = 12.dp,
            titleBottomSpacing = 14.dp
        ),
        sidebar = KovelyxSidebarComponentTokens(
            legacyCollapsedWidth = 72.dp,
            legacyExpandedWidth = 196.dp,
            collapsedWidth = 184.dp,
            expandedWidth = 230.dp,
            itemHeight = 52.dp,
            itemWidth = 148.dp,
            iconSize = 22.dp,
            leadingVisual = 34.dp,
            panelRadius = 30.dp,
            contentGap = 14.dp
        ),
        dialog = KovelyxDialogComponentTokens(
            maxWidth = 720.dp,
            contentPadding = 24.dp,
            cornerRadius = 16.dp,
            actionSpacing = 12.dp
        ),
        sidePanel = KovelyxDialogComponentTokens(
            maxWidth = 420.dp,
            contentPadding = 20.dp,
            cornerRadius = 20.dp,
            actionSpacing = 12.dp
        ),
        settings = KovelyxSettingsComponentTokens(
            containerRadius = 28.dp,
            secondaryCardRadius = 18.dp,
            railItemHeight = 56.dp,
            workspacePadding = 20.dp,
            rowGap = 16.dp
        ),
        player = KovelyxPlayerComponentTokens(
            overlayHorizontalPadding = 52.dp,
            overlayVerticalPadding = 36.dp,
            controlSize = 44.dp,
            sidePanelWidth = 360.dp,
            railWidth = 280.dp,
            progressHeight = 4.dp
        ),
        buttonHeight = 52.dp,
        chipHeight = 32.dp,
        badgeHeight = 20.dp,
        skeletonCornerRadius = 10.dp
    )
}
