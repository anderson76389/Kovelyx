package com.kovelyx.tv.core.image

/**
 * Bounds stale-poster revalidations on small TV boxes.
 *
 * Sync OkHttp calls do NOT honor Dispatcher's asynchronous maxRequests setting.
 * This gate limits concurrent calls and the remembered URL history explicitly.
 * Dropped background checks never block a cached poster from being displayed.
 */
internal class TvRevalidationBudget(
    private val maxRememberedUrls: Int = 256,
    private val maxConcurrentRequests: Int = 8,
    private val cooldownMs: Long = 10L * 60 * 1_000L,
) {
    init {
        require(maxRememberedUrls > 0 && maxConcurrentRequests > 0 && cooldownMs >= 0L)
    }

    private val inFlight = HashSet<String>()
    private val recentlyRevalidated = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > maxRememberedUrls
    }

    @Synchronized
    fun tryStart(url: String, nowMs: Long): Boolean {
        if (url in inFlight || inFlight.size >= maxConcurrentRequests) return false
        val previous = recentlyRevalidated[url]
        if (previous != null && nowMs >= previous && nowMs - previous <= cooldownMs) return false
        inFlight.add(url)
        return true
    }

    @Synchronized
    fun finish(url: String, nowMs: Long) {
        if (inFlight.remove(url)) recentlyRevalidated[url] = nowMs
    }

    @Synchronized
    internal fun rememberedCountForTest(): Int = recentlyRevalidated.size

    @Synchronized
    internal fun inFlightCountForTest(): Int = inFlight.size
}
