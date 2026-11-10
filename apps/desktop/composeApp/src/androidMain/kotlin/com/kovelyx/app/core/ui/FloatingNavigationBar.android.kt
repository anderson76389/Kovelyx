package com.kovelyx.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kovelyx.app.core.ui.glass.GlassBarSurface
import com.kovelyx.app.core.ui.jelly.JellyNavigationBar
import dev.chrisbanes.haze.HazeState

internal actual val floatingNavigationGlowSupported: Boolean
    get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

@Composable
internal actual fun FloatingNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier,
    scrollState: KovelyxNavBarScrollState?,
    hazeState: HazeState?,
    contentPadding: PaddingValues,
    compactSize: Boolean,
    glowEnabled: Boolean,
) {
    if (items.isEmpty()) return
    val showGlow = !floatingNavigationGlowSupported || glowEnabled
    val glowStrength by animateFloatAsState(
        targetValue = if (showGlow) 1f else 0f,
        animationSpec = tween(420, easing = KovelyxTokens.Motion.standard),
        label = "nav_glow_strength",
    )
    val labelFraction by animateFloatAsState(
        targetValue = scrollState?.labelVisibility ?: 1f,
        animationSpec = tween(KovelyxTokens.Motion.sheetEnterMillis, easing = KovelyxTokens.Motion.standard),
        label = "jelly_labels",
    )
    val trackHeight = 48.dp + (if (compactSize) 16.dp else 20.dp) * density.fontScale * labelFraction
    val horizontalPadding = 58.dp - 46.dp * labelFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        JellyNavigationBar(
            items = items,
            labelFraction = labelFraction,
            compactSize = compactSize,
            glowStrength = glowStrength,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(trackHeight),
        ) {
            GlassBarSurface(hazeState, Modifier.matchParentSize(), glowStrength)
        }
    }
}
