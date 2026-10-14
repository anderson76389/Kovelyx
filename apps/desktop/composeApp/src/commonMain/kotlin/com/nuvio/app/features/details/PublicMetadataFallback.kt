package com.nuvio.app.features.details

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.fetchAddonResponseText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Last-resort metadata provider for public IMDb movie / series IDs.
 *
 * Does not read Nuvio accounts or use any Nuvio endpoint. Primary user-added
 * metadata addons and a configured French TMDB source remain preferred.
 *
 * Cinemeta is an external public Stremio addon; an unavailable Cinemeta server
 * must NOT make the rest of Kovelyx unusable.
 */
internal object PublicMetadataFallback {
    private val log = Logger.withTag("PublicMetadataFallback")
    private const val BASE_URL = "https://v3-cinemeta.strem.io"
    private const val TIMEOUT_MS = 6_000L
    private val validImdbId = Regex("^tt[0-9]{7,12}$")

    internal fun buildMetadataUrl(type: String, id: String): String? {
        val normalizedType = when (type.lowercase()) {
            "movie" -> "movie"
            "series", "tv" -> "series"
            else -> return null
        }
        val normalizedId = id
            .trim()
            .removePrefix("imdb:")
            .substringBefore(':')
            .lowercase()
        if (!validImdbId.matches(normalizedId)) return null
        return "$BASE_URL/meta/$normalizedType/$normalizedId.json"
    }

    suspend fun fetch(type: String, id: String): MetaDetails? {
        val url = buildMetadataUrl(type, id) ?: return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val payload = fetchAddonResponseText(url)
                parseMetadataPayload(payload, type, id)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            log.w { "Public metadata unavailable: ${exception.message}" }
            null
        }
    }
    /** Parse separately to test provider responses without making network calls. */
    internal fun parseMetadataPayload(payload: String, type: String, id: String): MetaDetails? {
        val expectedUrl = buildMetadataUrl(type, id) ?: return null
        val expectedId = expectedUrl.substringAfterLast('/').removeSuffix(".json")
        val expectedType = if (type.lowercase() == "tv") "series" else type.lowercase()
        val meta = MetaDetailsParser.parse(payload)
        val actualType = if (meta.type.lowercase() == "tv") "series" else meta.type.lowercase()
        if (!meta.id.equals(expectedId, ignoreCase = true) ||
            meta.name.isBlank() ||
            actualType != expectedType
        ) {
            log.w { "Public metadata returned a mismatched item" }
            return null
        }
        return meta
    }

}
