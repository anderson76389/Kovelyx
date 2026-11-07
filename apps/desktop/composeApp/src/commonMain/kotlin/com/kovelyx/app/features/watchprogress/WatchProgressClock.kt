package com.kovelyx.app.features.watchprogress

internal expect object WatchProgressClock {
    fun nowEpochMs(): Long
}
