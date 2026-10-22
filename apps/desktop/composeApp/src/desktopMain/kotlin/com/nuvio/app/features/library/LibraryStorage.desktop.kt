package com.nuvio.app.features.library

import com.nuvio.app.core.cloud.CloudflareAccountClient

import com.nuvio.app.core.storage.DesktopStorage

internal actual object LibraryStorage {
    private val store = DesktopStorage.store("nuvio_library")

    private fun key(profileId: Int): String {
        val owner = CloudflareAccountClient.currentSession?.userId
            ?.takeIf { CloudflareAccountClient.enabled && it.matches(Regex("[a-zA-Z0-9_-]{4,128}")) }
        return if (owner == null) "library_$profileId"
            else "kovelyx_cloud_${owner}_library_$profileId"
    }

    actual fun loadPayload(profileId: Int): String? =
        store.getString(key(profileId))

    actual fun savePayload(profileId: Int, payload: String) {
        store.putString(key(profileId), payload)
    }
}
