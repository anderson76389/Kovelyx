package com.nuvio.app.core.sync

import com.nuvio.app.core.storage.DesktopStorage

internal actual object SyncClientIdentityStorage {
    private val store = DesktopStorage.store("nuvio_sync_client_identity")

    actual fun loadClientId(): String? =
        store.getString("client_instance_id")

    actual fun saveClientId(clientId: String) {
        store.putString("client_instance_id", clientId)
    }

    actual fun loadCloudflareDeletes(): String? = store.getString("cloudflare_deletes")
    actual fun saveCloudflareDeletes(payload: String) { store.putString("cloudflare_deletes", payload) }
    actual fun loadCloudflareAddons(key: String): String? = store.getString("addons.$key")
    actual fun saveCloudflareAddons(key: String, payload: String) { store.putString("addons.$key", payload) }
}
