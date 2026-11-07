package com.kovelyx.app.features.library

internal expect object LibraryClock {
    fun nowEpochMs(): Long
}
