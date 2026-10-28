package com.nuvio.tv.core.sync.library

import com.nuvio.tv.domain.model.LibraryDeltaEvent
import com.nuvio.tv.domain.model.LibrarySyncKey
import com.nuvio.tv.domain.model.SavedLibraryItem
import javax.inject.Inject
import javax.inject.Singleton

/** CloudflareTvDataSync performs live sync. Block retired backend RPCs safely. */
@Singleton
class RetiredLibrarySyncRemoteDataSource @Inject constructor() : LibrarySyncRemoteDataSource {
    private fun unavailable(): Nothing = error("Ancien serveur désactivé. Synchronisation du compte via Cloudflare D1 uniquement.")
    override suspend fun pullSnapshot(profileId: Int, pageSize: Int): List<SavedLibraryItem> = unavailable()
    override suspend fun getDeltaCursor(profileId: Int): Long = unavailable()
    override suspend fun pullDelta(profileId: Int, sinceEventId: Long, limit: Int): List<LibraryDeltaEvent> = unavailable()
    override suspend fun pushItems(profileId: Int, items: Collection<SavedLibraryItem>) { unavailable() }
    override suspend fun deleteItems(profileId: Int, keys: Collection<LibrarySyncKey>) { unavailable() }
}
