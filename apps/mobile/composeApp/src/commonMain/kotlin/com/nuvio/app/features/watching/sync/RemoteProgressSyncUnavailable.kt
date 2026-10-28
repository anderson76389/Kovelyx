package com.nuvio.app.features.watching.sync

import com.nuvio.app.features.watchprogress.WatchProgressEntry

/** Account synchronization is provided by CloudflareDataSync, never by legacy RPCs. */
internal object RemoteProgressSyncUnavailable : ProgressSyncAdapter {
    private fun unavailable(): Nothing = error("Synchronisation de progression : ancien serveur désactivé. Connectez Cloudflare.")
    override suspend fun pull(profileId: Int, sinceLastWatched: Long?, limit: Int?): List<ProgressSyncRecord> = unavailable()
    override suspend fun getDeltaCursor(profileId: Int): Long = unavailable()
    override suspend fun pullDelta(profileId: Int, sinceEventId: Long, limit: Int): List<ProgressDeltaEvent> = unavailable()
    override suspend fun push(profileId: Int, entries: Collection<WatchProgressEntry>) { unavailable() }
    override suspend fun delete(profileId: Int, entries: Collection<WatchProgressEntry>) { unavailable() }
}
