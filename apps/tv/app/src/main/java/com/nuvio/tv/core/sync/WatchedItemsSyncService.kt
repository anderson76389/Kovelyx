package com.nuvio.tv.core.sync

import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.model.WatchedMutationKey
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

data class WatchedItemsRemoteSyncResult(
    val upsertedItems: Int,
    val deletedItems: Int,
    val usedSnapshot: Boolean,
    val preservedLocalItems: Boolean
)

/** Watched flags and season/episode history are synced by Kovelyx D1 only. */
@Singleton
class WatchedItemsSyncService @Inject constructor(
    private val cloud: Lazy<CloudflareTvDataSync>,
    private val watched: WatchedItemsPreferences,
    private val mutations: WatchStateMutationStore,
    private val profileManager: ProfileManager
) {
    suspend fun pushToRemote(profileId: Int = profileManager.activeProfileId.value): Result<Unit> = runCatching {
        cloud.get().synchronize(profileId)
    }

    suspend fun pushItemsToRemote(
        items: Collection<WatchedItem>,
        updateLastSuccessfulPush: Boolean = false,
        profileId: Int = profileManager.activeProfileId.value
    ): Result<Unit> = runCatching {
        mutations.queueWatchedUpserts(items, profileId)
        cloud.get().synchronize(profileId)
    }

    suspend fun pullFromRemote(
        profileId: Int = profileManager.activeProfileId.value
    ): Result<List<WatchedItem>> = runCatching {
        cloud.get().synchronize(profileId)
        watched.getAllItems(profileId)
    }

    suspend fun syncDeltaFromRemote(
        profileId: Int = profileManager.activeProfileId.value
    ): Result<WatchedItemsRemoteSyncResult> = runCatching {
        cloud.get().synchronize(profileId)
        WatchedItemsRemoteSyncResult(0, 0, usedSnapshot = true, preservedLocalItems = true)
    }

    suspend fun syncSnapshotFromRemote(
        profileId: Int = profileManager.activeProfileId.value
    ): Result<WatchedItemsRemoteSyncResult> = syncDeltaFromRemote(profileId)

    suspend fun deleteFromRemote(
        contentId: String,
        season: Int?,
        episode: Int?,
        profileId: Int = profileManager.activeProfileId.value
    ): Result<Unit> = runCatching {
        mutations.queueWatchedDeletes(listOf(WatchedMutationKey(contentId, season, episode)), profileId)
        cloud.get().synchronize(profileId)
    }

    suspend fun deleteFromRemoteBatch(
        contentId: String,
        episodes: List<Pair<Int, Int>>,
        profileId: Int = profileManager.activeProfileId.value
    ): Result<Unit> = runCatching {
        mutations.queueWatchedDeletes(episodes.map { (season, episode) ->
            WatchedMutationKey(contentId, season, episode)
        }, profileId)
        cloud.get().synchronize(profileId)
    }
}
