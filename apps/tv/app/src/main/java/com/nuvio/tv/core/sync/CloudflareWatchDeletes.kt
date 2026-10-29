package com.nuvio.tv.core.sync

internal data class CloudflareDeleteRow(
    val itemKey: String,
    val revision: Int,
    val deleted: Boolean,
    val contentId: String = "",
    val season: Int? = null,
    val episode: Int? = null
)

internal data class CloudflareDeletedKeys(
    val progress: Set<String>,
    val watched: Set<String>
)

/** Flush the existing durable TV outbox before applying the snapshot that predates these deletes. */
internal suspend fun flushCloudflareWatchDeletes(
    profileId: Int,
    mutations: WatchStateMutationStore,
    progressRows: Map<String, CloudflareDeleteRow>,
    watchedRows: List<CloudflareDeleteRow>,
    write: suspend (category: String, key: String, revision: Int) -> Unit
): CloudflareDeletedKeys {
    val progressDeletes = mutations.pendingProgressDeletes(profileId)
    for (key in progressDeletes) {
        if (key.isBlank() || key.length > 200) continue
        val row = progressRows[key]
        if (row?.deleted != true) write("watch_progress", key, row?.revision ?: 0)
        mutations.acknowledgeProgressDeletes(listOf(key), profileId)
    }
    val watchedKeys = mutableSetOf<String>()
    for (pending in mutations.pendingWatchedDeletes(profileId)) {
        val matching = watchedRows.filter { row ->
            row.contentId == pending.contentId &&
                ((pending.season == null && pending.episode == null) ||
                    (row.season == pending.season && row.episode == pending.episode))
        }
        for (row in matching) {
            if (!row.deleted) write("watched_items", row.itemKey, row.revision)
            watchedKeys += row.itemKey
        }
        // No row exists after a successful complete pull, or every matching row was deleted.
        mutations.acknowledgeWatchedDeletes(listOf(pending), profileId)
    }
    return CloudflareDeletedKeys(progressDeletes, watchedKeys)
}
