package com.kovelyx.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

data class KovelyxTypeScale(
    val labelXs: TextStyle,
    val labelSm: TextStyle,
    val bodySm: TextStyle,
    val bodyMd: TextStyle,
    val bodyLg: TextStyle,
    val titleSm: TextStyle,
    val titleMd: TextStyle,
    val titleLg: TextStyle,
    val displaySm: TextStyle,
    val displayMd: TextStyle,
)

internal val LocalKovelyxTypeScale = staticCompositionLocalOf {
    KovelyxTypeScale(
        labelXs = TextStyle(fontSize = KovelyxTokens.Type.labelXs, lineHeight = KovelyxTokens.LineHeight.labelXs, fontWeight = FontWeight.Medium),
        labelSm = TextStyle(fontSize = KovelyxTokens.Type.labelSm, lineHeight = KovelyxTokens.LineHeight.labelSm, fontWeight = FontWeight.Medium),
        bodySm = TextStyle(fontSize = KovelyxTokens.Type.bodySm, lineHeight = KovelyxTokens.LineHeight.bodySm, fontWeight = FontWeight.Normal),
        bodyMd = TextStyle(fontSize = KovelyxTokens.Type.bodyMd, lineHeight = KovelyxTokens.LineHeight.bodyMd, fontWeight = FontWeight.Normal),
        bodyLg = TextStyle(fontSize = KovelyxTokens.Type.bodyLg, lineHeight = KovelyxTokens.LineHeight.bodyLg, fontWeight = FontWeight.Medium),
        titleSm = TextStyle(fontSize = KovelyxTokens.Type.titleSm, lineHeight = KovelyxTokens.LineHeight.titleSm, fontWeight = FontWeight.Bold),
        titleMd = TextStyle(fontSize = KovelyxTokens.Type.titleMd, lineHeight = KovelyxTokens.LineHeight.titleMd, fontWeight = FontWeight.Bold),
        titleLg = TextStyle(fontSize = KovelyxTokens.Type.titleLg, lineHeight = KovelyxTokens.LineHeight.titleLg, fontWeight = FontWeight.Bold),
        displaySm = TextStyle(fontSize = KovelyxTokens.Type.displaySm, lineHeight = KovelyxTokens.LineHeight.displaySm, fontWeight = FontWeight.ExtraBold),
        displayMd = TextStyle(fontSize = KovelyxTokens.Type.displayMd, lineHeight = KovelyxTokens.LineHeight.displayMd, fontWeight = FontWeight.ExtraBold),
    )
}

val MaterialTheme.nuvioTypeScale: KovelyxTypeScale
    @Composable
    get() = LocalKovelyxTypeScale.current
