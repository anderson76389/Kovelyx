package com.nuvio.app.features.watching.sync

import com.nuvio.app.features.watched.WatchedItem

/** CloudflareDataSync handles watched history; don't silently erase local data on a missing backend. */
internal object RemoteWatchedSyncUnavailable : WatchedSyncAdapter {
    private fun unavailable(): Nothing = error("Historique de visionnage : ancien serveur désactivé. Connectez Cloudflare.")
    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> = unavailable()
    override suspend fun getDeltaCursor(profileId: Int): Long = unavailable()
    override suspend fun pullDelta(profileId: Int, sinceEventId: Long, limit: Int): List<WatchedDeltaEvent> = unavailable()
    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) { unavailable() }
    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) { unavailable() }
}
