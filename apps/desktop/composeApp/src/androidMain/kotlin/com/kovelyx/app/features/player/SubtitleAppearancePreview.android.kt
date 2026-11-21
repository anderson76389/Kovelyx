package com.kovelyx.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

@Composable
internal actual fun rememberImportedSubtitlePreviewFont(choice: String, bold: Boolean): FontFamily? {
    val context = LocalContext.current
    return remember(context, choice, bold) {
        if (SubtitleFontChoice.isCustom(choice)) FontFamily(AndroidSubtitleFontStore.typeface(context, choice, bold))
        else null
    }
}
