package com.kovelyx.tv.domain.model

/** Source is part of the rating: never display a TMDB score as IMDb. */
data class EpisodeRating(val value: Double, val source: String)

fun validEpisodeRating(value: Double?, source: String): EpisodeRating? =
    value?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }?.let { EpisodeRating(it, source) }
