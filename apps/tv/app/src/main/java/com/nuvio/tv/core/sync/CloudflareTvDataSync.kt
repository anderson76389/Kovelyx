package com.nuvio.tv.core.sync

import android.util.Log
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.domain.model.LibraryDeltaEvent
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.model.librarySyncIdentity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Android TV Cloudflare synchronization. Shares the mobile/desktop JSON contract
 * and never makes a Supabase RPC. Only active, authenticated profiles are synced.
 * Pending library writes are pushed before remote reconciliation.
 */
@Singleton
class CloudflareTvDataSync @Inject constructor(
    private val profileManager: ProfileManager,
    private val library: LibraryPreferences,
    private val progress: WatchProgressPreferences,
    private val watched: WatchedItemsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    // A local edit made during a network sync must be retried once the current
    // snapshot finishes, not silently discarded by the inFlight gate.
    private val rerunRequested = ConcurrentHashMap.newKeySet<Int>()

    init {
        // Observe actual local TV changes (player position, library, watched flags).
        // Only different snapshots trigger an upload; echo updates don't loop.
        scope.launch {
            profileManager.activeProfileId.collectLatest { profileId ->
                combine(
                    library.libraryItems,
                    progress.observeAllRawProgress(profileId),
                    watched.observeAllItems(profileId)
                ) { libraryItems, positions, history ->
                    Triple(libraryItems, positions, history).hashCode()
                }.distinctUntilChanged()
                    .debounce(2500L)
                    .collect {
                        if (CloudflareTvAccountClient.account != null) request(profileId)
                    }
            }
        }
    }

    fun request(profileId: Int) {
        if (!CloudflareTvAccountClient.enabled) return
        if (!inFlight.add(profileId)) {
            rerunRequested.add(profileId)
            return
        }
        scope.launch {
            try {
                do {
                    rerunRequested.remove(profileId)
                    synchronize(profileId)
                    // Include edits received after the snapshot was read.
                } while (rerunRequested.remove(profileId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "D1 sync failed; local playback/library kept", error)
            } finally {
                inFlight.remove(profileId)
                // Close the race between the last loop condition and removing
                // the in-flight marker. A pending edit always gets a new run.
                if (rerunRequested.remove(profileId)) request(profileId)
            }
        }
    }

    suspend fun synchronize(profileId: Int) = mutex.withLock {
        if (CloudflareTvAccountClient.account == null ||
            profileManager.activeProfileId.value != profileId) return@withLock
        // Never equate the numeric local ID with an ordinal in the cloud list:
        // deleted/reordered profiles would silently mix another person's history.
        val localProfile = profileManager.profiles.value
            .firstOrNull { it.id == profileId } ?: return@withLock
        val remoteProfiles = CloudflareTvAccountClient.cloudProfiles()
        val linkedUuid = localProfile.cloudProfileId
        val matchingProfiles = remoteProfiles.filter {
            it.second.equals(localProfile.name, ignoreCase = true)
        }
        val uuid = when {
            linkedUuid != null && remoteProfiles.any { it.first == linkedUuid } -> linkedUuid
            linkedUuid != null -> {
                // An old linked UUID is not part of this account. Never silently
                // switch the local history into another profile or another owner.
                Log.w(TAG, "Profil Cloudflare lié inaccessible ; aucune fusion automatique")
                return@withLock
            }
            matchingProfiles.size == 1 -> matchingProfiles.single().first
            remoteProfiles.size == 1 && profileId == 1 -> remoteProfiles.single().first
            remoteProfiles.isEmpty() && profileId == 1 -> {
                // A TV-first account has no D1 profiles yet. Previously every
                // sync was silently skipped forever until another device
                // created a profile. Create the default only on empty accounts.
                CloudflareTvAccountClient.createCloudProfile(localProfile.name)
            }
            else -> {
                Log.w(TAG, "Profil TV ambigu : synchronisation Cloudflare non effectuée")
                return@withLock
            }
        }
        if (localProfile.cloudProfileId != uuid) {
            profileManager.linkCloudProfile(profileId, uuid)
        }
        val remote = CloudflareTvAccountClient.syncRows(uuid).associateBy {
            it.optString("category") + "|" + it.optString("itemKey")
        }
        syncLibrary(profileId, uuid, remote)
        syncProgress(profileId, uuid, remote)
        syncWatched(profileId, uuid, remote)
    }

    private fun revision(record: JSONObject?): Int = record?.optInt("revision") ?: 0
    private fun deleted(record: JSONObject): Boolean = record.optBoolean("deleted", false)
    private fun payload(record: JSONObject?): JSONObject? = record?.optJSONObject("payload")

    private suspend fun syncLibrary(profileId: Int, uuid: String, remote: Map<String, JSONObject>) {
        val before = library.getSyncState(profileId)
        val pendingDeletes = before.pendingDeleteKeys.mapTo(mutableSetOf()) {
            librarySyncIdentity(it.contentId, it.contentType)
        }
        for (item in before.pendingDeleteKeys) {
            val key = librarySyncIdentity(item.contentId, item.contentType)
            val record = remote["library|$key"]
            if (record != null && deleted(record)) continue
            CloudflareTvAccountClient.writeSync(uuid, "library", key,
                JSONObject().put("id", item.contentId).put("type", item.contentType),
                revision(record), deleted = true)
        }
        for (item in before.items) {
            val key = librarySyncIdentity(item.id, item.type)
            if (key in pendingDeletes || key.length > 200) continue
            val record = remote["library|$key"]
            val remoteAdded = if (record != null && deleted(record)) {
                record.optLong("updatedAt", Long.MAX_VALUE)
            } else payload(record)?.optLong("savedAtEpochMs", Long.MIN_VALUE)
                ?: Long.MIN_VALUE
            if (record != null && item.addedAt <= remoteAdded) continue
            val itemData = JSONObject()
                .put("id", item.id).put("type", item.type)
                .put("name", item.name.take(500)).put("savedAtEpochMs", item.addedAt)
            item.poster?.takeIf { it.startsWith("https://") }?.let {
                itemData.put("poster", it.substringBefore('?').substringBefore('#').take(1500))
            }
            CloudflareTvAccountClient.writeSync(uuid, "library", key, itemData, revision(record))
        }
        library.acknowledgePush(profileId, before.mutationRevision)
        val local = before.items.associateBy { librarySyncIdentity(it.id, it.type) }
        val pendingUpserts = before.pendingUpsertKeys.mapTo(mutableSetOf()) {
            librarySyncIdentity(it.contentId, it.contentType)
        }
        var cursor = before.deltaCursorEventId
        val events = mutableListOf<LibraryDeltaEvent>()
        for ((key, record) in remote) {
            if (!key.startsWith("library|")) continue
            val idKey = key.removePrefix("library|")
            if (idKey in pendingDeletes || idKey in pendingUpserts) continue
            val data = payload(record) ?: continue
            val item = SavedLibraryItem(
                id = data.optString("id"),
                type = data.optString("type"),
                name = data.optString("name"),
                poster = data.optString("poster").takeIf { it.isNotBlank() },
                posterShape = PosterShape.POSTER,
                background = null,
                description = null,
                releaseInfo = null,
                imdbRating = null,
                genres = emptyList(),
                addonBaseUrl = null,
                addedAt = data.optLong("savedAtEpochMs", 0L)
            )
            if (item.id.isBlank() || item.type.isBlank()) continue
            val current = local[idKey]
            if (deleted(record) && current == null) continue
            if (deleted(record) && current != null &&
                current.addedAt > record.optLong("updatedAt", Long.MAX_VALUE)) continue
            if (!deleted(record) && current != null && current.addedAt >= item.addedAt) continue
            cursor += 1L
            events += LibraryDeltaEvent(cursor, if (deleted(record)) "delete" else "upsert", item)
        }
        if (events.isNotEmpty()) library.applyRemoteDelta(profileId, events)
    }

    private suspend fun syncProgress(profileId: Int, uuid: String, remote: Map<String, JSONObject>) {
        val local = progress.getAllRawEntries(profileId)
        for ((key, record) in remote) {
            if (!key.startsWith("watch_progress|")) continue
            val localKey = key.removePrefix("watch_progress|")
            if (deleted(record)) {
                val existing = local[localKey]
                if (existing != null &&
                    record.optLong("updatedAt", Long.MAX_VALUE) >= existing.lastWatched) {
                    progress.removeProgress(existing.contentId, existing.season, existing.episode, profileId)
                }
                continue
            }
            val data = payload(record) ?: continue
            val contentId = data.optString("parentMetaId")
            if (contentId.isBlank()) continue
            val season = data.optInt("seasonNumber").takeIf { data.has("seasonNumber") }
            val episode = data.optInt("episodeNumber").takeIf { data.has("episodeNumber") }
            val lastUpdated = data.optLong("lastUpdatedEpochMs")
            val existing = local[localKey]
            if (existing != null && existing.lastWatched >= lastUpdated) continue
            progress.saveProgress(WatchProgress(
                contentId = contentId,
                contentType = data.optString("parentMetaType", "movie"),
                name = data.optString("title"),
                poster = null, backdrop = null, logo = null,
                videoId = data.optString("videoId"),
                season = season, episode = episode, episodeTitle = null,
                position = data.optLong("lastPositionMs"),
                duration = data.optLong("durationMs"),
                lastWatched = lastUpdated
            ), profileId)
        }
        for ((key, item) in local) {
            val cloudKey = key.take(200)
            if (cloudKey.isBlank()) continue
            val record = remote["watch_progress|$cloudKey"]
            val cloudDate = if (record != null && deleted(record)) {
                record.optLong("updatedAt", Long.MAX_VALUE)
            } else payload(record)?.optLong("lastUpdatedEpochMs", Long.MIN_VALUE)
                ?: Long.MIN_VALUE
            if (record != null && item.lastWatched <= cloudDate) continue
            val data = JSONObject()
                .put("contentType", item.contentType)
                .put("parentMetaId", item.contentId)
                .put("parentMetaType", item.contentType)
                .put("videoId", item.videoId)
                .put("title", item.name.take(500))
                .put("lastPositionMs", item.position)
                .put("durationMs", item.duration)
                .put("lastUpdatedEpochMs", item.lastWatched)
                .put("isCompleted", item.isCompleted())
            item.season?.let { data.put("seasonNumber", it) }
            item.episode?.let { data.put("episodeNumber", it) }
            CloudflareTvAccountClient.writeSync(uuid, "watch_progress", cloudKey, data, revision(record))
        }
    }

    private fun watchedKey(item: WatchedItem): String =
        "${item.contentType.trim()}:${item.contentId.trim()}:${item.season ?: -1}:${item.episode ?: -1}"

    private suspend fun syncWatched(profileId: Int, uuid: String, remote: Map<String, JSONObject>) {
        val local = watched.getAllItems(profileId)
        val localByKey = local.associateBy(::watchedKey)
        val additions = mutableListOf<WatchedItem>()
        val removals = mutableListOf<Triple<String, Int?, Int?>>()
        for ((key, record) in remote) {
            if (!key.startsWith("watched_items|")) continue
            val localKey = key.removePrefix("watched_items|")
            if (deleted(record)) {
                val existing = localByKey[localKey]
                if (existing != null && existing.watchedAt <=
                    record.optLong("updatedAt", Long.MAX_VALUE)) {
                    removals += Triple(existing.contentId, existing.season, existing.episode)
                }
                continue
            }
            val data = payload(record) ?: continue
            val contentId = data.optString("id")
            if (contentId.isBlank()) continue
            val season = data.optInt("season").takeIf { data.has("season") }
            val episode = data.optInt("episode").takeIf { data.has("episode") }
            val at = data.optLong("markedAtEpochMs")
            val existing = localByKey[localKey]
            if (existing == null || at > existing.watchedAt) additions += WatchedItem(
                contentId = contentId, contentType = data.optString("type"),
                title = data.optString("name"),
                season = season, episode = episode, watchedAt = at
            )
        }
        if (additions.isNotEmpty() || removals.isNotEmpty()) {
            watched.applyRemoteChanges(additions, removals, profileId = profileId)
        }
        for (item in local) {
            val key = watchedKey(item)
            if (key.length > 200) continue
            val record = remote["watched_items|$key"]
            val remoteTime = if (record != null && deleted(record)) {
                record.optLong("updatedAt", Long.MAX_VALUE)
            } else payload(record)?.optLong("markedAtEpochMs", Long.MIN_VALUE)
                ?: Long.MIN_VALUE
            if (record != null && item.watchedAt <= remoteTime) continue
            val data = JSONObject()
                .put("id", item.contentId).put("type", item.contentType)
                .put("name", item.title.take(500))
                .put("markedAtEpochMs", item.watchedAt)
            item.season?.let { data.put("season", it) }
            item.episode?.let { data.put("episode", it) }
            CloudflareTvAccountClient.writeSync(uuid, "watched_items", key, data, revision(record))
        }
    }

    private companion object { const val TAG = "KovelyxTvD1Sync" }
}
