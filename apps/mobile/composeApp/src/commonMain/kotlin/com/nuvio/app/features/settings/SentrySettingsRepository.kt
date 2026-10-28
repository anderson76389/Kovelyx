package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object SentrySettingsRepository {
    val isSupported: Boolean
        get() = false // Crash reporting is removed from Kovelyx.

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _enabled.value = false
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        // Ignore the historical preference, including enabled values from older builds.
        _enabled.value = false
        SentrySettingsStorage.saveEnabled(false)
    }
}
