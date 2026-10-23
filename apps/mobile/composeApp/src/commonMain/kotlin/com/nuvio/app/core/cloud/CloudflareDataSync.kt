package com.nuvio.app.core.cloud

import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.libraryItemKey
import com.nuvio.app.features.library.sync.LibraryDeltaEvent
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.watchprogress.resolvedProgressKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Cloudflare-first synchronization of local libraries, playback positions and watched history.
 * Never calls an upstream Supabase RPC. Merges additions and newer playback positions, with
 * tombstones for local library deletes. Remote writes use the revision read from the server;
 * a 409 leaves the local mutation pending for a fresh pull rather than overwriting blindly.
 */
object CloudflareDataSync {
    private val log = Logger.withTag("KovelyxD1Sync")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var pending: Job? = null

    fun request(profileId: Int) {
        if (!CloudflareAccountClient.enabled) return
        pending?.cancel()
        pending = scope.launch {
            delay(900L)
            try {
                synchronize(profileId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.e(error) { "Synchronisation D1 interrompue, données locales préservées" }
            }
        }
    }

    suspend fun synchronize(profileId: Int) = mutex.withLock {
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return@withLock
        if (account.isAnonymous || CloudflareAccountClient.currentSession?.userId != account.userId ||
            ProfileRepository.activeProfileId != profileId) return@withLock
        var cloudId = ProfileRepository.state.value.profiles
            .firstOrNull { it.profileIndex == profileId }?.id.orEmpty()
        if (cloudId.isBlank()) {
            ProfileRepository.pullProfiles()
            cloudId = ProfileRepository.state.value.profiles
                .firstOrNull { it.profileIndex == profileId }?.id.orEmpty()
        }
        if (!cloudId.matches(Regex("[a-fA-F0-9-]{36}"))) {
            log.w { "Aucun UUID Cloudflare pour le profil local $profileId" }
            return@withLock
        }
        val records = CloudflareAccountClient.synchronizeAll(cloudId)
        val remote = records.associateBy {
            it["category"]?.jsonPrimitive?.content.orEmpty() + "|" +
                it["itemKey"]?.jsonPrimitive?.content.orEmpty()
        }
        synchronizeLibrary(profileId, cloudId, remote)
        synchronizeProgress(profileId, cloudId, remote)
        synchronizeWatched(profileId, cloudId, remote)
    }

    fun deleteProgress(profileId: Int, entries: Collection<WatchProgressEntry>) {
        enqueueDeletes(
            profileId, "watch_progress",
            entries.map { it.resolvedProgressKey().take(200) }
        )
    }

    fun deleteWatched(profileId: Int, items: Collection<WatchedItem>) {
        enqueueDeletes(
            profileId, "watched_items",
            items.map { watchedItemKey(it.type, it.id, it.season, it.episode) }
        )
    }

    private fun enqueueDeletes(profileId: Int, category: String, keys: Collection<String>) {
        if (!CloudflareAccountClient.enabled || keys.isEmpty()) return
        scope.launch {
            try {
                mutex.withLock {
                    val account = AuthRepository.state.value as? AuthState.Authenticated
                        ?: return@withLock
                    if (account.isAnonymous ||
                        CloudflareAccountClient.currentSession?.userId != account.userId) return@withLock
                    val profileIdCloud = ProfileRepository.state.value.profiles
                        .firstOrNull { it.profileIndex == profileId }?.id.orEmpty()
                    if (!profileIdCloud.matches(Regex("[a-fA-F0-9-]{36}"))) return@withLock
                    val remote = CloudflareAccountClient.synchronizeAll(profileIdCloud).associateBy {
                        it["category"]?.jsonPrimitive?.content.orEmpty() + "|" +
                            it["itemKey"]?.jsonPrimitive?.content.orEmpty()
                    }
                    for (key in keys) {
                        if (key.isBlank() || key.length > 200) continue
                        val row = remote["$category|$key"]
                        if (row != null && isDeleted(row)) continue
                        CloudflareAccountClient.writeSync(
                            profileIdCloud, category, key,
                            buildJsonObject { put("removed", true) },
                            revision(row), deleted = true
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.e(error) { "Suppression Cloudflare non confirmée ; données locales préservées" }
            }
        }
    }

    private fun isDeleted(row: JsonObject): Boolean =
        row["deleted"]?.jsonPrimitive?.booleanOrNull == true

    private fun revision(row: JsonObject?): Int =
        row?.get("revision")?.jsonPrimitive?.intOrNull ?: 0

    private suspend fun synchronizeLibrary(
        profileId: Int, cloudId: String, remote: Map<String, JsonObject>
    ) {
        val snapshot = LibraryRepository.cloudSnapshotForSync(profileId) ?: return
        val pendingDeletes = snapshot.pendingDeleteKeys.mapTo(mutableSetOf()) {
            libraryItemKey(it.contentId, it.contentType)
        }
        for (deleted in snapshot.pendingDeleteKeys) {
            val key = libraryItemKey(deleted.contentId, deleted.contentType)
            val row = remote["library|$key"]
            if (row != null && isDeleted(row)) continue
            CloudflareAccountClient.writeSync(
                cloudId, "library", key,
                buildJsonObject {
                    put("id", deleted.contentId)
                    put("type", deleted.contentType)
                },
                revision = revision(row), deleted = true
            )
        }
        for (item in snapshot.items) {
            val key = libraryItemKey(item.id, item.type)
            if (key in pendingDeletes || key.length > 200) continue
            val row = remote["library|$key"]
            if (row != null && isDeleted(row)) continue
            val remoteDate = row?.get("payload")?.jsonObject
                ?.get("savedAtEpochMs")?.jsonPrimitive?.longOrNull ?: Long.MIN_VALUE
            if (row != null && item.savedAtEpochMs <= remoteDate) continue
            CloudflareAccountClient.writeSync(
                cloudId, "library", key, libraryPayload(item), revision(row)
            )
        }
        LibraryRepository.acknowledgeCloudSnapshot(snapshot)
        val events = mutableListOf<LibraryDeltaEvent>()
        val localByKey = snapshot.items.associateBy { libraryItemKey(it.id, it.type) }
        var eventId = snapshot.deltaCursorEventId
        for ((key, row) in remote) {
            if (!key.startsWith("library|")) continue
            val itemKey = key.removePrefix("library|")
            if (itemKey in pendingDeletes || snapshot.pendingUpsertKeys.any {
                libraryItemKey(it.contentId, it.contentType) == itemKey
            }) continue
            val payload = row["payload"]?.jsonObject ?: continue
            val item = runCatching { json.decodeFromJsonElement(LibraryItem.serializer(), payload) }
                .getOrNull() ?: continue
            val current = localByKey[itemKey]
            if (!isDeleted(row) && current != null && current.savedAtEpochMs >= item.savedAtEpochMs) continue
            if (isDeleted(row) && current == null) continue
            eventId += 1L
            events += LibraryDeltaEvent(eventId, if (isDeleted(row)) "delete" else "upsert", item)
        }
        LibraryRepository.applyCloudChanges(profileId, events)
    }

    private fun libraryPayload(item: LibraryItem): JsonObject = buildJsonObject {
        put("id", item.id)
        put("type", item.type)
        put("name", item.name.take(500))
        put("savedAtEpochMs", item.savedAtEpochMs)
        item.poster?.takeIf { it.startsWith("https://") }?.let {
            put("poster", it.substringBefore('?').substringBefore('#').take(1500))
        }
    }

    private suspend fun synchronizeProgress(
        profileId: Int, cloudId: String, remote: Map<String, JsonObject>
    ) {
        val local = WatchProgressRepository.cloudLocalEntries(profileId)
        val localByKey = local.associateBy { it.resolvedProgressKey().take(200) }
        val incoming = mutableListOf<WatchProgressEntry>()
        val remoteDeletes = mutableSetOf<String>()
        for ((key, row) in remote) {
            if (!key.startsWith("watch_progress|")) continue
            if (isDeleted(row)) {
                remoteDeletes += key.removePrefix("watch_progress|")
                continue
            }
            val payload = row["payload"]?.jsonObject ?: continue
            val entry = runCatching { json.decodeFromJsonElement(WatchProgressEntry.serializer(), payload) }
                .getOrNull() ?: continue
            val localEntry = localByKey[key.removePrefix("watch_progress|")]
            if (localEntry == null || entry.lastUpdatedEpochMs > localEntry.lastUpdatedEpochMs) {
                incoming += entry
            }
        }
        if (remoteDeletes.isNotEmpty()) WatchProgressRepository.applyCloudProgressDeletes(profileId, remoteDeletes)
        if (incoming.isNotEmpty()) WatchProgressRepository.applyExternalProgress(incoming)
        for (entry in local) {
            val key = entry.resolvedProgressKey().take(200)
            if (key.isBlank()) continue
            val row = remote["watch_progress|$key"]
            if (row != null && isDeleted(row)) continue
            val remoteDate = row?.get("payload")?.jsonObject
                ?.get("lastUpdatedEpochMs")?.jsonPrimitive?.longOrNull ?: Long.MIN_VALUE
            if (row != null && entry.lastUpdatedEpochMs <= remoteDate) continue
            CloudflareAccountClient.writeSync(
                cloudId, "watch_progress", key, progressPayload(entry), revision(row)
            )
        }
    }

    private fun progressPayload(entry: WatchProgressEntry): JsonObject = buildJsonObject {
        put("contentType", entry.contentType)
        put("parentMetaId", entry.parentMetaId)
        put("parentMetaType", entry.parentMetaType)
        put("videoId", entry.videoId)
        put("title", entry.title.take(500))
        put("lastPositionMs", entry.lastPositionMs)
        put("durationMs", entry.durationMs)
        put("lastUpdatedEpochMs", entry.lastUpdatedEpochMs)
        put("isCompleted", entry.isCompleted)
        entry.seasonNumber?.let { put("seasonNumber", it) }
        entry.episodeNumber?.let { put("episodeNumber", it) }
    }

    private suspend fun synchronizeWatched(
        profileId: Int, cloudId: String, remote: Map<String, JsonObject>
    ) {
        val local = WatchedRepository.cloudLocalItems(profileId)
        val localByKey = local.associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
        val incoming = mutableListOf<WatchedItem>()
        val remoteDeletes = mutableSetOf<String>()
        for ((key, row) in remote) {
            if (!key.startsWith("watched_items|")) continue
            if (isDeleted(row)) {
                remoteDeletes += key.removePrefix("watched_items|")
                continue
            }
            val item = runCatching {
                json.decodeFromJsonElement(WatchedItem.serializer(), row["payload"]!!.jsonObject)
            }.getOrNull() ?: continue
            val current = localByKey[key.removePrefix("watched_items|")]
            if (current == null || item.markedAtEpochMs > current.markedAtEpochMs) incoming += item
        }
        if (remoteDeletes.isNotEmpty()) WatchedRepository.applyCloudDeletedWatched(profileId, remoteDeletes)
        if (incoming.isNotEmpty()) WatchedRepository.applyCloudWatched(incoming)
        for (item in local) {
            val key = watchedItemKey(item.type, item.id, item.season, item.episode)
            if (key.length > 200) continue
            val row = remote["watched_items|$key"]
            if (row != null && isDeleted(row)) continue
            val remoteDate = row?.get("payload")?.jsonObject
                ?.get("markedAtEpochMs")?.jsonPrimitive?.longOrNull ?: Long.MIN_VALUE
            if (row != null && item.markedAtEpochMs <= remoteDate) continue
            CloudflareAccountClient.writeSync(
                cloudId, "watched_items", key, buildJsonObject {
                    put("id", item.id)
                    put("type", item.type)
                    put("name", item.name.take(500))
                    put("markedAtEpochMs", item.markedAtEpochMs)
                    item.season?.let { put("season", it) }
                    item.episode?.let { put("episode", it) }
                }, revision(row)
            )
        }
    }
}
