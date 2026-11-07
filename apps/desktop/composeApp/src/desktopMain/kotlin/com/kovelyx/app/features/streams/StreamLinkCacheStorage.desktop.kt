package com.kovelyx.app.features.streams

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.storage.ProfileScopedKey

internal actual object StreamLinkCacheStorage {
    private val store = DesktopStorage.store("nuvio_stream_link_cache")

    actual fun loadEntry(hashedKey: String): String? =
        store.getString(ProfileScopedKey.of(hashedKey))

    actual fun saveEntry(hashedKey: String, payload: String) {
        store.putString(ProfileScopedKey.of(hashedKey), payload)
    }

    actual fun removeEntry(hashedKey: String) {
        store.remove(ProfileScopedKey.of(hashedKey))
    }
}
