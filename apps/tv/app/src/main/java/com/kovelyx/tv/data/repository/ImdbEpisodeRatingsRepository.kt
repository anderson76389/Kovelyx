package com.kovelyx.tv.data.repository

import android.util.Log
import com.kovelyx.tv.domain.model.EpisodeRating
import com.kovelyx.tv.domain.model.validEpisodeRating
import com.kovelyx.tv.data.remote.api.TmdbApi
import com.kovelyx.tv.core.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import com.kovelyx.tv.data.remote.api.ImdbTapframeApi
import com.kovelyx.tv.data.remote.api.SeriesGraphApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImdbEpisodeRatingsRepository @Inject constructor(
    private val imdbTapframeApi: ImdbTapframeApi,
    private val seriesGraphApi: SeriesGraphApi,
    private val tmdbApi: TmdbApi,
    private val tmdbService: TmdbService
) {
    private data class CacheEntry(
        val ratings: Map<Pair<Int, Int>, EpisodeRating>,
        val expiresAtMs: Long
    )

    private val tag = "ImdbEpisodeRatingsRepo"
    private val cacheTtlMs = 30L * 60L * 1000L
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, kotlinx.coroutines.Deferred<Map<Pair<Int, Int>, EpisodeRating>>>()
    private val inFlightMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        expectedEpisodes: Set<Pair<Int, Int>>
    ): Map<Pair<Int, Int>, EpisodeRating> {
        val normalizedImdbId = imdbId
            ?.trim()
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
            ?.substringBefore(':')
        val normalizedTmdbId = tmdbId?.takeIf { it > 0 }
        if (normalizedImdbId == null && normalizedTmdbId == null) return emptyMap()

        val cacheKey = "${normalizedImdbId ?: ""}:$normalizedTmdbId:${expectedEpisodes.sortedWith(compareBy({ it.first }, { it.second })).joinToString(",")}"

        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (cached.expiresAtMs > now) return cached.ratings
            cache.remove(cacheKey)
        }

        val deferred = inFlightMutex.withLock {
            inFlight[cacheKey] ?: scope.async {
                try {
                    fetchEpisodeRatings(
                        imdbId = normalizedImdbId,
                        tmdbId = normalizedTmdbId,
                        expectedEpisodes = expectedEpisodes
                    ).also { result ->
                        if (cache.size >= 32) cache.keys.firstOrNull()?.let { cache.remove(it) }
                        cache[cacheKey] = CacheEntry(
                            ratings = result,
                            expiresAtMs = System.currentTimeMillis() + if (result.keys.containsAll(expectedEpisodes) && result.isNotEmpty()) cacheTtlMs else 60_000L
                        )
                    }
                } finally {
                    inFlightMutex.withLock {
                        inFlight.remove(cacheKey)
                    }
                }
            }.also { created ->
                inFlight[cacheKey] = created
            }
        }

        return deferred.await()
    }

    private suspend fun fetchEpisodeRatings(
        imdbId: String?,
        tmdbId: Int?,
        expectedEpisodes: Set<Pair<Int, Int>>
    ): Map<Pair<Int, Int>, EpisodeRating> {
        val ratings = linkedMapOf<Pair<Int, Int>, EpisodeRating>()
        val resolvedImdbId = imdbId ?: tmdbId?.let { tmdbService.tmdbToImdb(it, "series") }
        if (!resolvedImdbId.isNullOrBlank()) ratings.putAll(fetchFromImdbTapframe(resolvedImdbId))
        if (tmdbId != null && (ratings.isEmpty() || !ratings.keys.containsAll(expectedEpisodes))) {
            fetchFromSeriesGraph(tmdbId).forEach { (key, value) ->
                if (key !in ratings) ratings[key] = value
            }
            val apiKey = tmdbService.apiKey()
            if (apiKey.isNotBlank()) {
                for (season in (expectedEpisodes - ratings.keys).map { it.first }.distinct().sorted()) {
                    try {
                        val response = tmdbApi.getTvSeasonDetails(tmdbId, season, apiKey)
                        if (!response.isSuccessful) continue
                        response.body()?.episodes.orEmpty().forEach { episode ->
                            val number = episode.episodeNumber ?: return@forEach
                            val key = season to number
                            if (key !in expectedEpisodes || key in ratings || (episode.voteCount ?: 0) <= 0) return@forEach
                            validEpisodeRating(episode.voteAverage, "TMDB")?.let { ratings[key] = it }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Log.w(tag, "TMDB episode ratings unavailable for season $season")
                    }
                }
            }
        }
        return ratings
    }

    private suspend fun fetchFromImdbTapframe(imdbId: String): Map<Pair<Int, Int>, EpisodeRating> {
        return try {
            val response = imdbTapframeApi.getSeasonRatings(imdbId)
            if (!response.isSuccessful) {
                Log.w(tag, "Failed primary season ratings for imdbId=$imdbId (${response.code()})")
                return emptyMap()
            }
            toRatingsMap(response.body().orEmpty(), "IMDb")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(tag, "Error fetching primary season ratings for imdbId=$imdbId", e)
            emptyMap()
        }
    }

    private suspend fun fetchFromSeriesGraph(tmdbId: Int): Map<Pair<Int, Int>, EpisodeRating> {
        return try {
            val response = seriesGraphApi.getSeasonRatings(tmdbId)
            if (!response.isSuccessful) {
                Log.w(tag, "Failed fallback season ratings for tmdbId=$tmdbId (${response.code()})")
                return emptyMap()
            }
            toRatingsMap(response.body().orEmpty(), "SeriesGraph")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(tag, "Error fetching fallback season ratings for tmdbId=$tmdbId", e)
            emptyMap()
        }
    }

    private fun toRatingsMap(payload: List<com.kovelyx.tv.data.remote.api.SeriesGraphSeasonRatingsDto>, source: String): Map<Pair<Int, Int>, EpisodeRating> {
        return buildMap {
            payload.forEach { season ->
                season.episodes.orEmpty().forEach { episode ->
                    val seasonNumber = episode.seasonNumber ?: return@forEach
                    val episodeNumber = episode.episodeNumber ?: return@forEach
                    val rating = validEpisodeRating(episode.voteAverage, source) ?: return@forEach
                    put(seasonNumber to episodeNumber, rating)
                }
            }
        }
    }
}
