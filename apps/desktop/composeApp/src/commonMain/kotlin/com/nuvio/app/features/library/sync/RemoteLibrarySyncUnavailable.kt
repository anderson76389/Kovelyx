package com.nuvio.app.features.library.sync

import com.nuvio.app.features.library.LibraryItem

/** The original remote RPC protocol is retired; CloudflareDataSync handles account sync. */
internal object RemoteLibrarySyncUnavailable : LibrarySyncAdapter {
    private fun unavailable(): Nothing = error("Synchronisation de bibliothèque : ancien serveur désactivé. Connectez Cloudflare.")
    override suspend fun pullSnapshot(profileId: Int, pageSize: Int): List<LibraryItem> = unavailable()
    override suspend fun getDeltaCursor(profileId: Int): Long = unavailable()
    override suspend fun pullDelta(profileId: Int, sinceEventId: Long, limit: Int): List<LibraryDeltaEvent> = unavailable()
    override suspend fun pushItems(profileId: Int, items: Collection<LibraryItem>) { unavailable() }
    override suspend fun deleteItems(profileId: Int, keys: Collection<LibrarySyncKey>) { unavailable() }
}
