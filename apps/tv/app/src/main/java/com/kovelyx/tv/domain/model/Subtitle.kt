package com.kovelyx.tv.domain.model

import androidx.compose.runtime.Immutable
import com.kovelyx.tv.ui.util.languageCodeToName

@Immutable
data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val addonName: String,
    val addonLogo: String?,
    val isStreamProvided: Boolean = false,
    val headers: Map<String, String>? = null
) {
    fun getDisplayLanguage(): String = languageCodeToName(lang)

    companion object {
        fun languageCodeToName(code: String): String = com.kovelyx.tv.ui.util.languageCodeToName(code)
    }
}
