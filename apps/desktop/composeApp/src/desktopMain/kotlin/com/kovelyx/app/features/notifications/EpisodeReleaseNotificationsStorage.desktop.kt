package com.kovelyx.app.features.notifications

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.storage.ProfileScopedKey

internal actual object EpisodeReleaseNotificationsStorage {
    private val store = DesktopStorage.store("nuvio_episode_release_notifications")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("episode_release_notifications"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("episode_release_notifications"), payload)
    }
}
