package com.nuvio.tv.core.sync

import android.util.Log
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.domain.model.WatchedMutationKey
import com.nuvio.tv.domain.model.mutationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lecture + écriture réelles de la reprise et de l'historique vu sur le même
 * profil D1 que la bibliothèque TV. Aucune exportation des données invitées.
 *
 * Les préférences TV et les mutations en attente sont séparées par identifiant
 * Better Auth dans ProfileDataStoreFactory. En cas de 409, déconnexion, ou
 * réseau absent, on garde les mutations locales pour la prochaine tentative.
 */
@Singleton
class CloudflareTvWatchSyncService @Inject constructor(
    private val profileManager: ProfileManager,
    private val progressPreferences: WatchProgressPreferences,
    private val watchedPreferences: WatchedItemsPreferences,
    private val mutations: WatchStateMutationStore
) {
    private data class CloudRow(val revision: Int, val updatedAt: Long, val deleted: Boolean, val payload: JSONObject)
    private val mutex = Mutex()

    private fun key(progress: WatchProgress): String =
        if (progress.season != null && progress.episode != null)
            "${progress.contentId}_s${progress.season}e${progress.episode}"
        else progress.contentId

    private fun key(item: WatchedItem): String =
        if (item.season != null && item.episode != null)
            "${item.contentId}_s${item.season}e${item.episode}"
        else item.contentId

    private fun remoteRows(input: List<JSONObject>): Map<String, CloudRow> {
        val rows = linkedMapOf<String, CloudRow>()
        input.forEach { raw ->
            val itemKey = raw.getString("itemKey")
            val revision = raw.getInt("revision")
            require(itemKey.length in 1..200 && revision > 0 && itemKey !in rows)
            rows[itemKey] = CloudRow(
                revision = revision, updatedAt = raw.optLong("updatedAt", 0L),
                deleted = raw.getBoolean("deleted"), payload = raw.getJSONObject("payload")
            )
        }
        return rows
    }

    private fun readProgress(itemKey: String, row: CloudRow): WatchProgress {
        val item = row.payload.getJSONObject("entry")
        val contentId = item.getString("parentMetaId")
        val season = item.optInt("seasonNumber", -1).takeIf { it >= 0 }
        val episode = item.optInt("episodeNumber", -1).takeIf { it >= 0 }
        val type = item.optString("contentType", "movie")
        val duration = item.optLong("durationMs", 0).coerceAtLeast(0)
        val position = item.optLong("lastPositionMs", 0).coerceAtLeast(0)
        val updated = item.optLong("lastUpdatedEpochMs", 0)
        require(contentId.isNotBlank() && itemKey == (
            if (season != null && episode != null) "${contentId}_s${season}e$episode" else contentId
        ))
        require(updated > 0 && duration >= 0 && position >= 0)
        // videoId n'est repris que s'il s'agit d'un identifiant non sensible.
        val rawVideoId = item.optString("videoId").take(180)
        return WatchProgress(
            contentId = contentId, contentType = type,
            name = item.optString("title", contentId).ifBlank { contentId },
            poster = item.optString("poster").takeUnless(String::isBlank),
            backdrop = item.optString("background").takeUnless(String::isBlank),
            logo = item.optString("logo").takeUnless(String::isBlank),
            videoId = rawVideoId.takeUnless { it.contains("://") || it.contains('?') }
                ?: contentId,
            season = season, episode = episode,
            episodeTitle = item.optString("episodeTitle").takeUnless(String::isBlank),
            position = position, duration = duration,
            lastWatched = updated, source = WatchProgress.SOURCE_LOCAL,
            progressPercent = if (item.optBoolean("isCompleted")) 100f else null
        )
    }

    private fun safeTmdbImage(value: String?): String? = value?.takeIf {
        it.length <= 1024 && it.startsWith("https://image.tmdb.org/t/p/") &&
            '?' !in it && '#' !in it
    }

    private fun progressPayload(progress: WatchProgress): JSONObject = JSONObject().apply {
        require(progress.contentId.length in 1..150 &&
            "://" !in progress.contentId && '?' !in progress.contentId)

        put("parentMetaId", progress.contentId)
        put("parentMetaType", progress.contentType)
        put("contentType", progress.contentType)
        put("title", progress.name.ifBlank { progress.contentId })
        put("videoId", progress.videoId.takeUnless {
            it.contains("://") || it.contains('?') || it.length > 180
        } ?: progress.contentId)
        progress.season?.let { put("seasonNumber", it) }
        progress.episode?.let { put("episodeNumber", it) }
        progress.episodeTitle?.let { put("episodeTitle", it) }
        safeTmdbImage(progress.poster)?.let { put("poster", it) }
        safeTmdbImage(progress.backdrop)?.let { put("background", it) }
        safeTmdbImage(progress.logo)?.let { put("logo", it) }
        put("lastPositionMs", progress.position.coerceAtLeast(0))
        put("durationMs", progress.duration.coerceAtLeast(0))
        put("lastUpdatedEpochMs", progress.lastWatched.coerceAtLeast(1))
        put("isCompleted", progress.isCompleted())
        put("source", "local")
    }

    private fun readWatched(itemKey: String, row: CloudRow): WatchedItem {
        val item = row.payload.getJSONObject("item")
        val contentId = item.getString("contentId")
        val type = item.getString("contentType")
        val season = item.optInt("season", -1).takeIf { it >= 0 }
        val episode = item.optInt("episode", -1).takeIf { it >= 0 }
        val watchedAt = item.getLong("watchedAt")
        require(watchedAt > 0 && type in setOf("movie", "series", "tv"))
        val result = WatchedItem(
            contentId = contentId, contentType = type,
            title = item.getString("title"), season = season,
            episode = episode, watchedAt = watchedAt
        )
        require(itemKey == key(result)) { "Épisode cloud incompatible" }
        return result
    }

    private fun watchedPayload(item: WatchedItem) = JSONObject().put("item",
        JSONObject()
            .put("contentId", item.contentId)
            .put("contentType", if (item.contentType == "movie") "movie" else "series")
            .put("title", item.title.ifBlank { item.contentId }.take(300))
            .put("watchedAt", item.watchedAt.coerceAtLeast(1))
            .apply {
                item.season?.let { put("season", it) }
                item.episode?.let { put("episode", it) }
            }
    )

    private fun deletedWatchedKey(key: String): Triple<String, Int?, Int?> {
        val pattern = Regex("^(.*)_s([0-9]+)e([0-9]+)$").matchEntire(key)
        if (pattern == null) return Triple(key, null, null)
        return Triple(
            pattern.groupValues[1],
            pattern.groupValues[2].toIntOrNull(),
            pattern.groupValues[3].toIntOrNull()
        )
    }

    suspend fun sync(profileId: Int): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = CloudflareTvAccountClient.account ?: return@withLock false
            val binding = CloudflareTvLibraryStore.state.value
                ?.takeIf { it.ownerId == owner.id && it.localProfileId == profileId }
                ?: return@withLock false
            if (!CloudflareTvLibraryStore.isBound(profileId)) return@withLock false
            val cloudProfile = binding.cloudProfileId
            fun current(): Boolean =
                CloudflareTvAccountClient.account == owner &&
                    CloudflareTvLibraryStore.state.value?.let {
                        it.ownerId == owner.id && it.localProfileId == profileId &&
                            it.cloudProfileId == cloudProfile
                    } == true &&
                    profileManager.activeProfileId.value == profileId

            try {
                // Ne jamais importer des pages incomplètes. Les deux lectures
                // doivent réussir avant toute modification des données locales.
                val progressRows = remoteRows(CloudflareTvAccountClient.readProgress(cloudProfile))
                val watchedRows = remoteRows(CloudflareTvAccountClient.readWatched(cloudProfile))
                val progressRemote = progressRows.mapValues { (key, row) ->
                    if (row.deleted) null else readProgress(key, row)
                }
                val watchedRemote = watchedRows.mapValues { (key, row) ->
                    if (row.deleted) null else readWatched(key, row)
                }
                if (!current()) return@withLock false

                val pendingProgress = mutations.pendingProgressUpserts(profileId)
                val pendingProgressDeletes = mutations.pendingProgressDeletes(profileId)
                val pendingWatched = mutations.pendingWatchedUpserts(profileId)
                val pendingWatchedDeletes = mutations.pendingWatchedDeletes(profileId)
                val localProgress = progressPreferences.getAllRawEntries(profileId)
                val localWatched = watchedPreferences.getAllItems(profileId)
                    .associateBy(::key)

                val progressUpserts = linkedMapOf<String, WatchProgress>()
                val progressDeletes = mutableListOf<String>()
                for ((k, remote) in progressRemote) {
                    val row = checkNotNull(progressRows[k])
                    val local = localProgress[k]
                    if (k in pendingProgress || k in pendingProgressDeletes) continue
                    if (local != null && local.lastWatched > (remote?.lastWatched ?: row.updatedAt)) {
                        mutations.queueProgressUpserts(mapOf(k to local), profileId)
                        continue
                    }
                    if (remote == null) progressDeletes += k
                    else progressUpserts[k] = remote
                }
                val watchedUpserts = mutableListOf<WatchedItem>()
                val watchedDeletes = mutableListOf<Triple<String, Int?, Int?>>()
                for ((k, remote) in watchedRemote) {
                    val row = checkNotNull(watchedRows[k])
                    val local = localWatched[k]
                    val mk = (remote ?: local)?.mutationKey()
                    if (mk != null && (mk in pendingWatched || mk in pendingWatchedDeletes)) continue
                    if (local != null && local.watchedAt > (remote?.watchedAt ?: row.updatedAt)) {
                        mutations.queueWatchedUpserts(listOf(local), profileId)
                        continue
                    }
                    if (remote == null) watchedDeletes += deletedWatchedKey(k)
                    else watchedUpserts += remote
                }
                if (!current()) return@withLock false
                progressPreferences.applyRemoteChanges(
                    upserts = progressUpserts, deletes = progressDeletes,
                    pendingUpsertKeys = pendingProgress.keys,
                    pendingDeleteKeys = pendingProgressDeletes,
                    profileId = profileId
                )
                watchedPreferences.applyRemoteChanges(
                    upserts = watchedUpserts, deletes = watchedDeletes,
                    pendingUpsertKeys = pendingWatched.keys,
                    pendingDeleteKeys = pendingWatchedDeletes,
                    profileId = profileId
                )

                // Envoi des modifications TV, y compris celles écrites hors ligne.
                // Les révisions prises dans le snapshot précédent garantissent
                // qu'un appareil concurrent ne sera jamais écrasé silencieusement.
                val progressDeleteTimes = mutations.pendingProgressDeleteTimes(profileId)
                for (k in mutations.pendingProgressDeletes(profileId)) {
                    if (!current()) return@withLock false
                    val row = progressRows[k]
                    // Si l'autre appareil a regardé ce titre APRÈS le retrait
                    // local, ne jamais écraser sa nouvelle progression.
                    if (row != null && !row.deleted &&
                        row.updatedAt > (progressDeleteTimes[k] ?: 0L)) continue
                    try {
                        if (row?.deleted != true) CloudflareTvAccountClient.writeProgress(
                            cloudProfile, k, null, row?.revision ?: 0, true
                        )
                        mutations.acknowledgeProgressDeletes(listOf(k), profileId)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        Log.w("TvCloudWatch", "Suppression progression en attente", error)
                    }
                }
                for ((k, entry) in mutations.pendingProgressUpserts(profileId)) {
                    if (!current()) return@withLock false
                    val row = progressRows[k]
                    val remote = progressRemote[k]
                    if (remote != null && remote.lastWatched >= entry.lastWatched) {
                        if (!current()) return@withLock false
                        val latest = progressPreferences.getAllRawEntries(profileId)[k]
                        if (latest == null || latest.lastWatched <= remote.lastWatched) {
                            progressPreferences.applyRemoteChanges(
                                upserts = mapOf(k to remote), deletes = emptyList(),
                                profileId = profileId
                            )
                            mutations.acknowledgeProgressUpserts(mapOf(k to entry), profileId)
                        }
                        continue
                    }
                    if (row?.deleted == true && row.updatedAt > entry.lastWatched) continue
                    try {
                        CloudflareTvAccountClient.writeProgress(
                            cloudProfile, k, progressPayload(entry), row?.revision ?: 0, false
                        )
                        mutations.acknowledgeProgressUpserts(mapOf(k to entry), profileId)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        Log.w("TvCloudWatch", "Progression en attente, données préservées", error)
                    }
                }
                val watchedDeleteTimes = mutations.pendingWatchedDeleteTimes(profileId)
                for (key in mutations.pendingWatchedDeletes(profileId)) {
                    if (!current()) return@withLock false
                    val k = if (key.season != null && key.episode != null)
                        "${key.contentId}_s${key.season}e${key.episode}" else key.contentId
                    val row = watchedRows[k]
                    if (row != null && !row.deleted &&
                        row.updatedAt > (watchedDeleteTimes[key] ?: 0L)) continue
                    try {
                        if (row?.deleted != true) CloudflareTvAccountClient.writeWatched(
                            cloudProfile, k, JSONObject(), row?.revision ?: 0, true
                        )
                        mutations.acknowledgeWatchedDeletes(listOf(key), profileId)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        Log.w("TvCloudWatch", "Suppression vu en attente", error)
                    }
                }
                for ((_, item) in mutations.pendingWatchedUpserts(profileId)) {
                    if (!current()) return@withLock false
                    val k = key(item)
                    val row = watchedRows[k]
                    val remote = watchedRemote[k]
                    if (remote != null && remote.watchedAt >= item.watchedAt) {
                        if (!current()) return@withLock false
                        val latest = watchedPreferences.getAllItems(profileId)
                            .firstOrNull { key(it) == k }
                        if (latest == null || latest.watchedAt <= remote.watchedAt) {
                            watchedPreferences.applyRemoteChanges(
                                upserts = listOf(remote), deletes = emptyList(),
                                profileId = profileId
                            )
                            mutations.acknowledgeWatchedUpserts(listOf(item), profileId)
                        }
                        continue
                    }
                    if (row?.deleted == true && row.updatedAt > item.watchedAt) continue
                    try {
                        CloudflareTvAccountClient.writeWatched(
                            cloudProfile, k, watchedPayload(item), row?.revision ?: 0, false
                        )
                        mutations.acknowledgeWatchedUpserts(listOf(item), profileId)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        Log.w("TvCloudWatch", "Historique vu en attente", error)
                    }
                }
                current()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w("TvCloudWatch", "Synchronisation reportée sans effacement local", error)
                false
            }
        }
    }
}
