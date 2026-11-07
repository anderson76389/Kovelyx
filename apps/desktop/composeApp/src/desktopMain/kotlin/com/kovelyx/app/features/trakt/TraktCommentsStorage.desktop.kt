package com.kovelyx.app.features.trakt

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.storage.ProfileScopedKey
import com.kovelyx.app.core.sync.decodeSyncBoolean
import com.kovelyx.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object TraktCommentsStorage {
    private const val enabledKey = "trakt_comments_enabled"
    private val store = DesktopStorage.store("nuvio_trakt_comments")

    actual fun loadEnabled(): Boolean? =
        store.getBoolean(ProfileScopedKey.of(enabledKey))

    actual fun saveEnabled(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(enabledKey), enabled)
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        store.remove(ProfileScopedKey.of(enabledKey))
        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
    }
}
