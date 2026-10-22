package com.nuvio.app.features.watched

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.cloud.CloudflareAccountClient

actual object WatchedStorage {
    private val store = DesktopStorage.store("nuvio_watched")

    private fun scopedKey(profileId: Int): String {
        val auth = AuthRepository.state.value as? AuthState.Authenticated
        val owner = auth?.userId?.takeIf {
            CloudflareAccountClient.enabled && !auth.isAnonymous
        }
        return accountScopedWatchedStorageKey(
            legacyKey = "watched_$profileId",
            profileId = profileId,
            cloudOwnerId = owner
        )
    }

    actual fun loadPayload(profileId: Int): String? =
        store.getString(scopedKey(profileId))

    actual fun savePayload(profileId: Int, payload: String) {
        store.putString(scopedKey(profileId), payload)
    }
}
