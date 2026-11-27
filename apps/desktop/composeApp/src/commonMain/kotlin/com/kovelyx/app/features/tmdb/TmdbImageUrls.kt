package com.kovelyx.app.features.tmdb

private val tmdbSizedImage = Regex("""^https://image\.tmdb\.org/t/p/(?:w|h)[0-9]+/""")

internal fun originalTmdbImageUrl(imageUrl: String?): String? =
    imageUrl?.replaceFirst(tmdbSizedImage, "https://image.tmdb.org/t/p/original/")
