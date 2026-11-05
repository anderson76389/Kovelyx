package com.nuvio.app.features.watching.sync

import com.nuvio.app.features.watched.WatchedItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The independent cloud synchronizer owns all remote data. Never clear offline state. */
object UnavailableWatchedSyncAdapter : WatchedSyncAdapter {
    private fun noCloud(): Nothing = error("Serveur Kovelyx Cloudflare non configuré")
    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> = noCloud()
    override suspend fun getDeltaCursor(profileId: Int): Long? = noCloud()
    override suspend fun pullDelta(profileId: Int, sinceEventId: Long, limit: Int): List<WatchedDeltaEvent> = noCloud()
    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) { noCloud() }
    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) { noCloud() }
}

@Serializable
private data class WatchedSyncItem(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long = 0,
)

@Serializable
private data class WatchedDeltaSyncItem(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long = 0,
)

@Serializable
private data class WatchedDeleteKey(
    @SerialName("content_id") val contentId: String,
    val season: Int? = null,
    val episode: Int? = null,
)
