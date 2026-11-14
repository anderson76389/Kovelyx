package com.kovelyx.app.features.player

import androidx.compose.runtime.Composable

/** Local-only font ID. A custom identifier is generated, not a filesystem path or URL. */
internal object SubtitleFontChoice {
    const val DEFAULT = "default"
    const val SANS_SERIF = "sans-serif"
    const val SERIF = "serif"
    const val MONOSPACE = "monospace"
    private val customId = Regex("^custom:[0-9a-f\\-]{36}\\.(ttf|otf)$")

    fun isValid(choice: String): Boolean = choice in
        setOf(DEFAULT, SANS_SERIF, SERIF, MONOSPACE) || customId.matches(choice)

    fun label(choice: String): String = when (choice) {
        DEFAULT -> "Par défaut"
        SANS_SERIF -> "Sans empattement"
        SERIF -> "Serif"
        MONOSPACE -> "Monospace"
        else -> if (customId.matches(choice)) "Police importée" else "Par défaut"
    }

    fun isCustom(choice: String): Boolean = customId.matches(choice)
    fun filename(choice: String): String? =
        if (isCustom(choice)) choice.removePrefix("custom:") else null
}

@Composable
internal expect fun SubtitleFontImportButton(onImported: (String) -> Unit)
