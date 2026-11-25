package com.kovelyx.app.features.details

import co.touchlab.kermit.Logger
import com.kovelyx.app.features.addons.httpRequestRaw
import com.kovelyx.app.features.tmdb.TmdbSettingsRepository
import com.kovelyx.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import com.kovelyx.app.features.library.LibraryClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ImdbEpisodeRatingsRepository {
    private data class CacheEntry(
        val ratings: Map<Pair<Int, Int>, EpisodeRating>,
        val expiresAtMs: Long,
    )

    private val log = Logger.withTag("ImdbEpisodeRatingsRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, Deferred<Map<Pair<Int, Int>, EpisodeRating>>>()

    suspend fun getEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        expectedEpisodes: Set<Pair<Int, Int>>,
    ): Map<Pair<Int, Int>, EpisodeRating> {
        val normalizedImdbId = normalizeImdbId(imdbId)
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return emptyMap()

        val cacheKey = "${normalizedImdbId ?: ""}:$normalizedTmdbId:${expectedEpisodes.sortedWith(compareBy({ it.first }, { it.second })).joinToString(",")}"
        val now = currentTimeMs()
        mutex.withLock {
            cache[cacheKey]?.let { cached ->
                if (cached.expiresAtMs > now) return cached.ratings
                cache.remove(cacheKey)
            }
        }

        val deferred = mutex.withLock {
            inFlight[cacheKey] ?: scope.async {
                try {
                    fetchEpisodeRatings(
                        imdbId = normalizedImdbId,
                        tmdbId = normalizedTmdbId,
                        expectedEpisodes = expectedEpisodes,
                    ).also { ratings ->
                        mutex.withLock {
                            if (cache.size >= 32) cache.remove(cache.keys.first())
                            cache[cacheKey] = CacheEntry(
                                ratings = ratings,
                                expiresAtMs = currentTimeMs() + if (ratings.keys.containsAll(expectedEpisodes) && ratings.isNotEmpty()) CACHE_TTL_MS else 60_000L,
                            )
                        }
                    }
                } finally {
                    mutex.withLock {
                        inFlight.remove(cacheKey)
                    }
                }
            }.also { created ->
                inFlight[cacheKey] = created
            }
        }

        return deferred.await()
    }

    fun clearCache() {
        cache.clear()
        inFlight.clear()
    }

    private suspend fun fetchEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        expectedEpisodes: Set<Pair<Int, Int>>,
    ): Map<Pair<Int, Int>, EpisodeRating> {
        val ratings = linkedMapOf<Pair<Int, Int>, EpisodeRating>()
        if (!imdbId.isNullOrBlank()) {
            ratings.putAll(toRatingsMap(ImdbTapframeApi.getSeasonRatings(imdbId), "IMDb"))
        }
        if (tmdbId != null && (ratings.isEmpty() || !ratings.keys.containsAll(expectedEpisodes))) {
            toRatingsMap(SeriesGraphApi.getSeasonRatings(tmdbId), "SeriesGraph").forEach { (key, value) ->
                if (key !in ratings) ratings[key] = value
            }
            val apiKey = TmdbSettingsRepository.effectiveApiKey()
            if (apiKey.isNotBlank()) {
                val missingSeasons = (expectedEpisodes - ratings.keys).map { it.first }.distinct().sorted()
                for (season in missingSeasons) {
                    fetchTmdbSeason(tmdbId, season, apiKey).forEach { (key, value) ->
                        if (key in expectedEpisodes && key !in ratings) ratings[key] = value
                    }
                }
            }
        }
        return ratings
    }

    @Serializable
    private data class TmdbSeason(val episodes: List<TmdbEpisode> = emptyList())

    @Serializable
    private data class TmdbEpisode(
        @SerialName("episode_number") val number: Int? = null,
        @SerialName("vote_average") val average: Double? = null,
        @SerialName("vote_count") val count: Int? = null,
    )

    private suspend fun fetchTmdbSeason(id: Int, season: Int, apiKey: String): Map<Pair<Int, Int>, EpisodeRating> {
        return try {
            val response = httpRequestRaw(
                method = "GET",
                url = buildTmdbUrl("tv/$id/season/$season", apiKey),
                headers = mapOf("Accept" to "application/json"),
                body = "",
            )
            if (response.status !in 200..299) return emptyMap()
            Json { ignoreUnknownKeys = true }.decodeFromString<TmdbSeason>(response.body).episodes.mapNotNull { ep ->
                val number = ep.number ?: return@mapNotNull null
                if ((ep.count ?: 0) <= 0) return@mapNotNull null
                val rating = validEpisodeRating(ep.average, "TMDB") ?: return@mapNotNull null
                (season to number) to rating
            }.toMap()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            log.w { "TMDB episode ratings unavailable for season $season" }
            emptyMap()
        }
    }

    private fun toRatingsMap(payload: List<SeriesGraphSeasonRatingsDto>, source: String): Map<Pair<Int, Int>, EpisodeRating> =
        buildMap {
            payload.forEach { season ->
                season.episodes.orEmpty().forEach { episode ->
                    val seasonNumber = episode.seasonNumber ?: return@forEach
                    val episodeNumber = episode.episodeNumber ?: return@forEach
                    val rating = validEpisodeRating(episode.voteAverage, source) ?: return@forEach
                    put(seasonNumber to episodeNumber, rating)
                }
            }
        }

    private fun normalizeImdbId(value: String?): String? =
        value
            ?.trim()
            ?.substringBefore(':')
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }

    private fun currentTimeMs(): Long = LibraryClock.nowEpochMs()

    private const val CACHE_TTL_MS = 30L * 60L * 1000L
}
