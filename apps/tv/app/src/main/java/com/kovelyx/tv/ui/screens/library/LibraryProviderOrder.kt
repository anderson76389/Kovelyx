package com.kovelyx.tv.ui.screens.library

import com.kovelyx.tv.domain.model.LibraryEntry
import com.kovelyx.tv.domain.model.LibrarySourceMode

data class LibraryProviderOrder(
    val source: LibrarySourceMode,
    val listKey: String,
    val sortOption: LibrarySortOption,
    val ranks: Map<String, Int>
) {
    fun comparator(): Comparator<LibraryEntry> = compareBy<LibraryEntry> {
        ranks["${it.type}:${it.id}"] ?: Int.MAX_VALUE
    }.thenBy { it.id }
}
