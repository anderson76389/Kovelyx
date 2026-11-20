package com.kovelyx.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.FileFont

@Composable
internal actual fun rememberImportedSubtitlePreviewFont(choice: String, bold: Boolean): FontFamily? {
    return remember(choice) {
        DesktopSubtitleFontStore.file(choice)?.let { font ->
            FontFamily(FileFont(font))
        }
    }
}
