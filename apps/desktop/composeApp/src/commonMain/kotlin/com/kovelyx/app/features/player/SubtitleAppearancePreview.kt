package com.kovelyx.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.kovelyx_subtitle_preview_sample
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubtitleAppearancePreview(style: SubtitleStyleState, modifier: Modifier = Modifier) {
    val sample = stringResource(Res.string.kovelyx_subtitle_preview_sample)
    val fontFamily = when (style.fontFamily) {
        SubtitleFontChoice.SANS_SERIF -> FontFamily.SansSerif
        SubtitleFontChoice.SERIF -> FontFamily.Serif
        SubtitleFontChoice.MONOSPACE -> FontFamily.Monospace
        else -> rememberImportedSubtitlePreviewFont(style.fontFamily, style.bold) ?: FontFamily.Default
    }
    val textStyle = TextStyle(
        color = style.textColor,
        fontFamily = fontFamily,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = style.fontSizeSp.sp,
        textAlign = TextAlign.Center,
    )
    val outlineWidth = with(LocalDensity.current) { style.outlineWidth.coerceIn(1, 5).dp.toPx() }
    val bottomPadding = (8 + style.bottomOffset.coerceIn(0, 200) * 0.25f).dp
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 136.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF222B3C), Color(0xFF5B6575))))
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.background(style.backgroundColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (style.outlineEnabled) {
                BasicText(
                    text = sample,
                    modifier = Modifier.clearAndSetSemantics { },
                    style = textStyle.copy(
                        color = style.outlineColor.copy(alpha = style.outlineColor.alpha * style.textColor.alpha),
                        drawStyle = Stroke(width = outlineWidth),
                    ),
                )
            }
            BasicText(text = sample, style = textStyle)
        }
    }
}

@Composable
internal expect fun rememberImportedSubtitlePreviewFont(choice: String, bold: Boolean): FontFamily?
