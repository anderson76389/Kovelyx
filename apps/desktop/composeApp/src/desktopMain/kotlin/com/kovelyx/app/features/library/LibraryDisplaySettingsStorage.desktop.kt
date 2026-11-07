package com.kovelyx.app.features.library

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.storage.ProfileScopedKey

internal actual object LibraryDisplaySettingsStorage {
    private const val payloadKey = "library_display_settings_payload"
    private val store = DesktopStorage.store("nuvio_library_display_settings")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of(payloadKey), payload)
    }
}
