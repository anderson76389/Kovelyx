package com.kovelyx.app.features.watching.sync

import com.kovelyx.app.features.watchprogress.WatchProgressEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The independent cloud synchronizer owns all remote data. Never clear offline state. */
object UnavailableProgressSyncAdapter : ProgressSyncAdapter {
    private fun noCloud(): Nothing = error("Serveur Kovelyx Cloudflare non configuré")
    override suspend fun getDeltaCursor(profileId: Int): Long? = noCloud()
    override suspend fun pullDelta(profileId: Int, sinceEventId: Long, limit: Int): List<ProgressDeltaEvent> = noCloud()
    override suspend fun pull(profileId: Int, sinceLastWatched: Long?, limit: Int?): List<ProgressSyncRecord> = noCloud()
    override suspend fun push(profileId: Int, entries: Collection<WatchProgressEntry>) { noCloud() }
    override suspend fun delete(profileId: Int, entries: Collection<WatchProgressEntry>) { noCloud() }
}

@Serializable
private data class WatchProgressSyncEntry(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("video_id") val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0,
    val duration: Long = 0,
    @SerialName("last_watched") val lastWatched: Long = 0,
    @SerialName("progress_key") val progressKey: String = "",
)

@Serializable
private data class WatchProgressDeltaSyncEntry(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("progress_key") val progressKey: String,
    @SerialName("content_id") val contentId: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("video_id") val videoId: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0,
    val duration: Long = 0,
    @SerialName("last_watched") val lastWatched: Long = 0,
)
