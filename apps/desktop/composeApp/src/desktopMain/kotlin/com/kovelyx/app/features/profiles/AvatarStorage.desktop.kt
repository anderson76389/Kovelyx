package com.kovelyx.app.features.profiles

import com.kovelyx.app.core.storage.DesktopStorage

internal actual object AvatarStorage {
    private val store = DesktopStorage.store("nuvio_avatars")

    actual fun loadPayload(): String? = store.getString("avatars")

    actual fun savePayload(payload: String) {
        store.putString("avatars", payload)
    }
}
