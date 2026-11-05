package com.nuvio.app.core.sync

/**
 * Media servers remain private to this device until a dedicated encrypted
 * Cloudflare sync is implemented. Never expose their access tokens to
 * retired databases or interpret a missing server as an empty remote list.
 */
object MediaServerSync {
    fun startObserving() = Unit
    fun clearAccountState() = Unit
    suspend fun syncFromRemote(profileId: Int): Boolean = false
}
