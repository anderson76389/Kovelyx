package com.nuvio.tv.core.sync

import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.domain.model.WatchProgress
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

data class WatchProgressRemoteSyncResult(
    val upsertedEntries: Int,
    val deletedEntries: Int,
    val usedSnapshot: Boolean,
    val preservedLocalItems: Boolean
)

/** D1/Better Auth is the only progress sync, with a durable offline deletion outbox. */
@Singleton
class WatchProgressSyncService @Inject constructor(
    private val cloud: Lazy<CloudflareTvDataSync>,
    private val progress: WatchProgressPreferences,
    private val mutationStore: WatchStateMutationStore,
    private val profileManager: ProfileManager
) {
    suspend fun deleteFromRemote(
        keys: Collection<String>,
        profileId: Int = profileManager.activeProfileId.value
    ): Result<Unit> = runCatching {
        mutationStore.queueProgressDeletes(keys, profileId)
        cloud.get().synchronize(profileId)
    }

    suspend fun pushToRemote(profileId: Int = profileManager.activeProfileId.value): Result<Unit> = runCatching {
        cloud.get().synchronize(profileId)
    }

    suspend fun pushSingleToRemote(
        key: String,
        progress: WatchProgress,
        profileId: Int = profileManager.activeProfileId.value
    ): Result<Unit> = runCatching {
        mutationStore.queueProgressUpserts(mapOf(key to progress), profileId)
        cloud.get().synchronize(profileId)
    }

    suspend fun pullFromRemote(
        profileId: Int = profileManager.activeProfileId.value,
        sinceLastWatched: Long? = null,
        limit: Int? = null
    ): Result<List<Pair<String, WatchProgress>>> = runCatching {
        cloud.get().synchronize(profileId)
        progress.getAllRawEntries(profileId).entries
            .map { it.key to it.value }
            .let { if (limit == null) it else it.take(limit.coerceAtLeast(0)) }
    }

    suspend fun syncDeltaFromRemote(
        profileId: Int = profileManager.activeProfileId.value
    ): Result<WatchProgressRemoteSyncResult> = runCatching {
        cloud.get().synchronize(profileId)
        WatchProgressRemoteSyncResult(0, 0, usedSnapshot = true, preservedLocalItems = true)
    }

    suspend fun syncSnapshotFromRemote(
        profileId: Int = profileManager.activeProfileId.value
    ): Result<WatchProgressRemoteSyncResult> = syncDeltaFromRemote(profileId)
}
