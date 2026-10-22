package com.nuvio.app.features.watchprogress

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.cloud.CloudflareAccountClient

internal actual object WatchProgressStorage {
    private val store = DesktopStorage.store("nuvio_watch_progress")

    private fun scopedKey(profileId: Int): String {
        val auth = AuthRepository.state.value as? AuthState.Authenticated
        val owner = auth?.userId?.takeIf {
            // L'identité de stockage reste celle de l'état Auth même
            // pendant la déconnexion (jeton déjà effacé) : ne jamais
            // retomber sur la clé des invités entre deux transitions.
            CloudflareAccountClient.enabled && !auth.isAnonymous
        }
        return accountScopedProgressStorageKey(
            legacyKey = "watch_progress_$profileId", profileId = profileId, cloudOwnerId = owner
        )
    }

    actual fun loadPayload(profileId: Int): String? =
        store.getString(scopedKey(profileId))

    actual fun savePayload(profileId: Int, payload: String) {
        store.putString(scopedKey(profileId), payload)
    }
}
