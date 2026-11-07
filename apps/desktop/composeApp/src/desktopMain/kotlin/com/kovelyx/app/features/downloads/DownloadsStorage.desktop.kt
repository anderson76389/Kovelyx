package com.kovelyx.app.features.downloads

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.storage.ProfileScopedKey

internal actual object DownloadsStorage {
    private val store = DesktopStorage.store("nuvio_downloads")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("downloads"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("downloads"), payload)
    }
}
