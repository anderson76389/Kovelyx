package com.nuvio.app.features.profiles

import com.nuvio.app.core.cloud.CloudflareAccountClient

import com.nuvio.app.core.storage.DesktopStorage

internal actual object ProfileStorage {
    private val store = DesktopStorage.store("nuvio_profiles")

    private fun key(): String {
        val owner = CloudflareAccountClient.currentSession?.userId
            ?.takeIf { CloudflareAccountClient.enabled && it.matches(Regex("[a-zA-Z0-9_-]{4,128}")) }
        return if (owner == null) "profiles" else "kovelyx_cloud_${owner}_profiles"
    }

    actual fun loadPayload(): String? = store.getString(key())

    actual fun savePayload(payload: String) {
        store.putString(key(), payload)
    }
}
