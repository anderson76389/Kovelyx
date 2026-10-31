package com.nuvio.app.core.sync

import platform.Foundation.NSUserDefaults

actual object SyncClientIdentityStorage {
    private const val clientIdKey = "client_instance_id"

    actual fun loadClientId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(clientIdKey)

    actual fun saveClientId(clientId: String) {
        NSUserDefaults.standardUserDefaults.setObject(clientId, forKey = clientIdKey)
    }

    actual fun loadCloudflareDeletes(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey("cloudflare_deletes")
    actual fun saveCloudflareDeletes(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = "cloudflare_deletes")
    }
    actual fun loadCloudflareAddons(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey("addons.$key")
    actual fun saveCloudflareAddons(key: String, payload: String) { NSUserDefaults.standardUserDefaults.setObject(payload, forKey = "addons.$key") }
}
