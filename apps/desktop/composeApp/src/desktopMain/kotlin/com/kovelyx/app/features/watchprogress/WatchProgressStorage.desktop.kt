package com.kovelyx.app.features.watchprogress

import com.kovelyx.app.core.storage.DesktopStorage

internal actual object WatchProgressStorage {
    private val store = DesktopStorage.store("nuvio_watch_progress")

    actual fun loadPayload(profileId: Int): String? =
        store.getString("watch_progress_$profileId")

    actual fun savePayload(profileId: Int, payload: String) {
        store.putString("watch_progress_$profileId", payload)
    }
}
