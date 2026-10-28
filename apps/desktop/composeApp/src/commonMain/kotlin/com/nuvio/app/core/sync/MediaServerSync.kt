package com.nuvio.app.core.sync

/**
 * Media-server credentials must stay on the device. The Cloudflare D1 Worker
 * explicitly disallows unencrypted tokens, so the former Supabase RPC is
 * retired without touching locally stored Plex/Jellyfin/Emby servers.
 *
 * Cloudflare account library, progress, and watched history sync separately.
 */
object MediaServerSync {
    fun startObserving() = Unit
    fun clearAccountState() = Unit
    suspend fun syncFromRemote(profileId: Int): Boolean = false
}
