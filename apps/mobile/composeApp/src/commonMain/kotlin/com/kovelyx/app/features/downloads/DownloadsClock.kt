package com.kovelyx.app.features.downloads

internal expect object DownloadsClock {
    fun nowEpochMs(): Long
}
