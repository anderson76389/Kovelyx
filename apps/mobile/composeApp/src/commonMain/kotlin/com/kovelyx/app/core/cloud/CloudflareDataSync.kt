package com.kovelyx.app.core.cloud

import co.touchlab.kermit.Logger
import com.kovelyx.app.core.sync.SyncClientIdentityStorage
import kotlin.random.Random
import kotlin.time.Clock
import com.kovelyx.app.core.auth.AuthRepository
import com.kovelyx.app.core.auth.AuthState
import com.kovelyx.app.features.library.LibraryItem
import com.kovelyx.app.features.library.LibraryRepository
import com.kovelyx.app.features.library.libraryItemKey
import com.kovelyx.app.features.library.sync.LibraryDeltaEvent
import com.kovelyx.app.features.profiles.ProfileRepository
import com.kovelyx.app.features.watched.WatchedItem
import com.kovelyx.app.features.watched.WatchedRepository
import com.kovelyx.app.features.watched.watchedItemKey
import com.kovelyx.app.features.watchprogress.WatchProgressEntry
import com.kovelyx.app.features.watchprogress.WatchProgressRepository
import com.kovelyx.app.features.watchprogress.resolvedProgressKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
 * Never calls an upstream Legacy RPC. Merges additions and newer playback positions, with
 * tombstones for local library deletes. Remote writes use the revision read from the server;
 * a 409 leaves the local mutation pending for a fresh pull rather than overwriting blindly.
 */
object CloudflareDataSync {
    private val health get() = com.kovelyx.maintenance.ui.SyncHealth
    private fun healthKey(owner: String, profileId: Int) = "$owner:$profileId"

    fun diagnosticText(): String {
        val user = AuthRepository.state.value as? AuthState.Authenticated
            ?: return "Mode local : aucun compte connecté"
        if (user.isAnonymous) return "Mode invité : aucune synchronisation cloud"
        val profile = ProfileRepository.activeProfileId
        val snapshot = LibraryRepository.cloudSnapshotForSync(profile)
        val count = snapshot?.let { it.pendingUpsertKeys.size + it.pendingDeleteKeys.size }
        return health.describe(healthKey(user.userId, profile)) + "\nBibliothèque : " +
            (count?.let { "$it modification(s) en attente" } ?: "profil pas encore chargé") +
            "\nLes autres catégories sont incluses dans le résultat global de synchronisation."
    }
    private val log = Logger.withTag("KovelyxD1Sync")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    // One worker for all devices' local profile mutations. A new player-position
    // update must NEVER cancel an in-flight D1 request: cancellation could drop
    // a successful upload or leave a 409 reconciliation without another retry.
    // Conflation queues the latest profile once, even during continuous playback.
    private val requests = Channel<Int>(Channel.CONFLATED)
    private val deletes = CloudflareDeleteOutbox(
        SyncClientIdentityStorage::loadCloudflareDeletes,
        SyncClientIdentityStorage::saveCloudflareDeletes
    )

    init {
        scope.launch {
            for (profileId in requests) {
                delay(900L)
                var retryDelayMs = 2_000L
                // A transient outage or HTTP 409 must not silently discard the
                // only queued upload. Each attempt pulls fresh D1 revisions.
                while (true) {
                    val user = AuthRepository.state.value as? AuthState.Authenticated
                    if (user == null || user.isAnonymous ||
                        ProfileRepository.activeProfileId != profileId ||
                        CloudflareAccountClient.currentSession?.userId != user.userId) break
                    try {
                        synchronize(profileId)
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // No local deletion. Retry while this same user/profile
                        // remains active, with a bounded exponential delay.
                        log.w(error) { "Cloudflare D1 indisponible : nouvelle tentative automatique, données conservées" }
                        delay(retryDelayMs)
                        retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000L)
                    }
                }
            }
        }
    }

    fun request(profileId: Int) {
        if (!CloudflareAccountClient.enabled) return
        (AuthRepository.state.value as? AuthState.Authenticated)?.let { health.queued(healthKey(it.userId, profileId)) }
        requests.trySend(profileId)
    }

    suspend fun synchronize(profileId: Int) = mutex.withLock {
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return@withLock
        if (account.isAnonymous || CloudflareAccountClient.currentSession?.userId != account.userId ||
            ProfileRepository.activeProfileId != profileId) return@withLock
        val diagnosticKey = healthKey(account.userId, profileId)
        val generation = health.started(diagnosticKey)
        try {
        var cloudId = ProfileRepository.state.value.profiles
            .firstOrNull { it.profileIndex == profileId }?.id.orEmpty()
        if (cloudId.isBlank()) {
            ProfileRepository.pullProfiles()
            cloudId = ProfileRepository.state.value.profiles
                .firstOrNull { it.profileIndex == profileId }?.id.orEmpty()
        }
        if (!cloudId.matches(Regex("[a-fA-F0-9-]{36}"))) {
            // Offline/new profile: try again after D1 profile creation.
            throw IllegalStateException("Profil Cloudflare non encore lié : reprise de synchronisation nécessaire")
        }
        val records = CloudflareAccountClient.synchronizeAll(cloudId)
        val remote = records.associateBy {
            it["category"]?.jsonPrimitive?.content.orEmpty() + "|" +
                it["itemKey"]?.jsonPrimitive?.content.orEmpty()
        }
        val suppressed = mutableSetOf<String>()
        for (pending in deletes.pending(account.userId, cloudId)) {
            check(CloudflareAccountClient.currentSession?.userId == account.userId &&
                ProfileRepository.activeProfileId == profileId) { "Session ou profil modifié" }
            val identity = "${pending.category}|${pending.itemKey}"
            val newerLocal = when (pending.category) {
                "watch_progress" -> WatchProgressRepository.cloudLocalEntries(profileId)
                    .any { it.resolvedProgressKey().take(200) == pending.itemKey &&
                        it.lastUpdatedEpochMs > pending.createdAtMs }
                "watched_items" -> WatchedRepository.cloudLocalItems(profileId)
                    .any { watchedItemKey(it.type, it.id, it.season, it.episode) == pending.itemKey &&
                        it.markedAtEpochMs > pending.createdAtMs }
                else -> false
            }
            if (!newerLocal) {
                val row = remote[identity]
                if (row == null || !isDeleted(row)) CloudflareAccountClient.writeSync(
                    cloudId, pending.category, pending.itemKey,
                    buildJsonObject { put("removed", true) }, revision(row), deleted = true
                )
                suppressed += identity
            }
            deletes.acknowledge(pending)
        }
        val reconciled = remote.filterKeys { it !in suppressed }
        synchronizeLibrary(profileId, cloudId, reconciled)
        synchronizeProgress(profileId, cloudId, reconciled, suppressed)
        synchronizeWatched(profileId, cloudId, reconciled, suppressed)
        // Essential account state must not wait behind large photos or third-party manifests/code.
        var addonsReady = true
        try { com.kovelyx.app.features.addons.AddonRepository.synchronizeCloudAddons(cloudId, records) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { addonsReady = false; log.w(error) { "Extensions Cloudflare différées, configuration locale conservée" } }
        val catalogueReady = CloudflareCatalogueSync.synchronize(profileId, cloudId, records)
        var pluginsReady = true
        try { pluginsReady = com.kovelyx.app.features.plugins.PluginRepository.synchronizeCloudPlugins(profileId, cloudId, records) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { pluginsReady = false; log.w(error) { "Modules complémentaires différés" } }
        var visualReady = true
        try { visualReady = CloudflareVisualSettingsSync.synchronize(profileId, cloudId, records) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { visualReady = false; log.w(error) { "Préférences visuelles différées" } }
        val profileImagesReady = ProfileRepository.syncCloudflareProfileImages()
        check(pluginsReady) { "Modules complémentaires en attente de synchronisation" }
        check(visualReady) { "Préférences visuelles en attente de synchronisation" }
        check(addonsReady) { "Extensions en attente de synchronisation" }
        check(catalogueReady) { "Catalogues en attente de synchronisation" }
        check(profileImagesReady) { "Images de profil en attente de synchronisation" }
        health.succeeded(diagnosticKey, generation)
        } catch (cancelled: CancellationException) {
            health.cancelled(diagnosticKey)
            throw cancelled
        } catch (error: Exception) {
            health.failed(diagnosticKey)
            throw error
        } finally { health.cancelled(diagnosticKey) }
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
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (account.isAnonymous || CloudflareAccountClient.currentSession?.userId != account.userId) return
        val cloudId = ProfileRepository.state.value.profiles
            .firstOrNull { it.profileIndex == profileId }?.id.orEmpty()
        if (!cloudId.matches(Regex("[a-fA-F0-9-]{36}"))) return
        val now = Clock.System.now().toEpochMilliseconds()
        for (key in keys) {
            if (key.isBlank() || key.length > 200) continue
            deletes.record(PendingCloudflareDelete(account.userId, cloudId, profileId,
                category, key, now, Random.nextLong()))
        }
        request(profileId)
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
            // A tombstone wins only until a genuinely newer local save.
            val remoteDate = if (row != null && isDeleted(row)) {
                row["updatedAt"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
            } else row?.get("payload")?.jsonObject
                ?.get("savedAtEpochMs")?.jsonPrimitive?.longOrNull ?: Long.MIN_VALUE
            if (row != null && (item.savedAtEpochMs < remoteDate ||
                (isDeleted(row) && item.savedAtEpochMs == remoteDate))) continue
            val localPayload = libraryPayload(item)
            val previous = row?.get("payload") as? JsonObject
            val outgoing = if (item.savedAtEpochMs == remoteDate && previous != null)
                com.kovelyx.cloud.library.enrichLibraryMetadata(previous, localPayload) else localPayload
            if (previous == outgoing) continue
            CloudflareAccountClient.writeSync(cloudId, "library", key, outgoing, revision(row))
        }
        LibraryRepository.acknowledgeCloudSnapshot(snapshot)
        val pendingUpserts = snapshot.pendingUpsertKeys.mapTo(mutableSetOf()) {
            libraryItemKey(it.contentId, it.contentType)
        }
        val events = mutableListOf<LibraryDeltaEvent>()
        val localByKey = snapshot.items.associateBy { libraryItemKey(it.id, it.type) }
        var eventId = snapshot.deltaCursorEventId
        for ((key, row) in remote) {
            if (!key.startsWith("library|")) continue
            val itemKey = key.removePrefix("library|")
            if (itemKey in pendingDeletes || itemKey in pendingUpserts) continue
            val current = localByKey[itemKey]
            if (isDeleted(row)) {
                // Deletions carry a small tombstone (often only id/type), not a
                // complete LibraryItem. Decode the local item instead of
                // silently skipping the deletion on Windows and Android.
                if (current == null) continue
                val deletedAt = row["updatedAt"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
                if (current.savedAtEpochMs > deletedAt) continue
                eventId += 1L
                events += LibraryDeltaEvent(eventId, "delete", current)
                continue
            }
            val payload = row["payload"]?.jsonObject ?: continue
            val item = runCatching { json.decodeFromJsonElement(LibraryItem.serializer(), payload) }
                .getOrNull() ?: continue
            if (current != null && current.savedAtEpochMs > item.savedAtEpochMs) continue
            val enriched = if (current?.savedAtEpochMs == item.savedAtEpochMs) {
                val currentPayload = json.encodeToJsonElement(LibraryItem.serializer(), current).jsonObject
                json.decodeFromJsonElement(LibraryItem.serializer(),
                    com.kovelyx.cloud.library.enrichLibraryMetadata(currentPayload, payload))
            } else item
            if (enriched == current) continue
            eventId += 1L
            events += LibraryDeltaEvent(eventId, "upsert", enriched)
        }
        LibraryRepository.applyCloudChanges(profileId, events)
    }

    private fun libraryPayload(item: LibraryItem): JsonObject = buildJsonObject {
        put("id", item.id)
        put("type", item.type)
        put("name", item.name.take(500))
        put("savedAtEpochMs", item.savedAtEpochMs)
        fun text(key: String, value: String?, limit: Int = 8192) {
            value?.takeIf { it.isNotBlank() && it.length <= limit }?.let { put(key, it) }
        }
        text("poster", item.poster)
        text("banner", item.banner)
        text("logo", item.logo)
        text("addonBaseUrl", item.addonBaseUrl)
        text("description", item.description, 8000)
        text("releaseInfo", item.releaseInfo, 500)
        text("imdbRating", item.imdbRating, 20)
        put("genres", JsonArray(item.genres.map(::JsonPrimitive)))
        put("posterShape", item.posterShape.name)
    }

    private suspend fun synchronizeProgress(
        profileId: Int, cloudId: String, remote: Map<String, JsonObject>, suppressed: Set<String>
    ) {
        val local = WatchProgressRepository.cloudLocalEntries(profileId)
        val localByKey = local.associateBy { it.resolvedProgressKey().take(200) }
        val incoming = mutableListOf<WatchProgressEntry>()
        val remoteDeletes = mutableMapOf<String, Long>()
        for ((key, row) in remote) {
            if (!key.startsWith("watch_progress|")) continue
            if (isDeleted(row)) {
                remoteDeletes[key.removePrefix("watch_progress|")] =
                    row["updatedAt"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
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
            if ("watch_progress|$key" in suppressed) continue
            if (key.isBlank()) continue
            val row = remote["watch_progress|$key"]
            val remoteDate = if (row != null && isDeleted(row)) {
                row["updatedAt"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
            } else row?.get("payload")?.jsonObject
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
        profileId: Int, cloudId: String, remote: Map<String, JsonObject>, suppressed: Set<String>
    ) {
        val local = WatchedRepository.cloudLocalItems(profileId)
        val localByKey = local.associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
        val incoming = mutableListOf<WatchedItem>()
        val remoteDeletes = mutableMapOf<String, Long>()
        for ((key, row) in remote) {
            if (!key.startsWith("watched_items|")) continue
            if (isDeleted(row)) {
                remoteDeletes[key.removePrefix("watched_items|")] =
                    row["updatedAt"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
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
            if ("watched_items|$key" in suppressed) continue
            if (key.length > 200) continue
            val row = remote["watched_items|$key"]
            val remoteDate = if (row != null && isDeleted(row)) {
                row["updatedAt"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
            } else row?.get("payload")?.jsonObject
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
