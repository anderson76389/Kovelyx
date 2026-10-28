package com.nuvio.app.features.library.sync

import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LibrarySyncItem(
    @SerialName("content_id") override val contentId: String,
    @SerialName("content_type") override val contentType: String,
    override val name: String = "",
    override val poster: String? = null,
    @SerialName("poster_shape") override val posterShape: String = "POSTER",
    override val background: String? = null,
    override val logo: String? = null,
    override val description: String? = null,
    @SerialName("release_info") override val releaseInfo: String? = null,
    @SerialName("imdb_rating") override val imdbRating: Float? = null,
    override val genres: List<String> = emptyList(),
    @SerialName("addon_base_url") override val addonBaseUrl: String? = null,
    @SerialName("added_at") override val addedAt: Long = 0,
) : LibrarySyncFields

@Serializable
internal data class LibraryDeltaSyncItem(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("content_id") override val contentId: String,
    @SerialName("content_type") override val contentType: String,
    override val name: String = "",
    override val poster: String? = null,
    @SerialName("poster_shape") override val posterShape: String = "POSTER",
    override val background: String? = null,
    override val logo: String? = null,
    override val description: String? = null,
    @SerialName("release_info") override val releaseInfo: String? = null,
    @SerialName("imdb_rating") override val imdbRating: Float? = null,
    override val genres: List<String> = emptyList(),
    @SerialName("addon_base_url") override val addonBaseUrl: String? = null,
    @SerialName("added_at") override val addedAt: Long = 0,
) : LibrarySyncFields

internal interface LibrarySyncFields {
    val contentId: String
    val contentType: String
    val name: String
    val poster: String?
    val posterShape: String
    val background: String?
    val logo: String?
    val description: String?
    val releaseInfo: String?
    val imdbRating: Float?
    val genres: List<String>
    val addonBaseUrl: String?
    val addedAt: Long
}

internal fun LibrarySyncFields.toLibraryItem(): LibraryItem =
    LibraryItem(
        id = contentId,
        type = contentType,
        name = name,
        poster = poster,
        banner = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toString(),
        genres = genres,
        posterShape = posterShape.toPosterShape(),
        addonBaseUrl = addonBaseUrl,
        savedAtEpochMs = addedAt,
    )

internal fun LibraryItem.toSyncItem(): LibrarySyncItem =
    LibrarySyncItem(
        contentId = id,
        contentType = type,
        name = name,
        poster = poster,
        posterShape = posterShape.toSyncName(),
        background = banner,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toFloatOrNull(),
        genres = genres,
        addonBaseUrl = addonBaseUrl,
        addedAt = savedAtEpochMs,
    )

private fun String.toPosterShape(): PosterShape =
    when (trim().uppercase()) {
        "LANDSCAPE" -> PosterShape.Landscape
        "SQUARE" -> PosterShape.Square
        else -> PosterShape.Poster
    }

private fun PosterShape.toSyncName(): String =
    when (this) {
        PosterShape.Poster -> "POSTER"
        PosterShape.Square -> "SQUARE"
        PosterShape.Landscape -> "LANDSCAPE"
    }
