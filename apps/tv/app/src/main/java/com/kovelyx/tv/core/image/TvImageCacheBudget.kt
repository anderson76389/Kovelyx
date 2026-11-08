package com.kovelyx.tv.core.image

/**
 * Bounded poster caches for low-memory TV boxes.
 * These limits only affect disposable Coil cache entries, never account data,
 * downloaded media, images belonging to profiles or playback buffers.
 */
internal object TvImageCacheBudget {
    private const val MIB = 1024L * 1024L

    fun diskBytes(totalRamMb: Long, constrainedTv: Boolean): Long = when {
        constrainedTv -> 128L * MIB
        totalRamMb <= 3072L -> 160L * MIB
        else -> 200L * MIB
    }

    fun memoryClassFraction(totalRamMb: Long, constrainedTv: Boolean): Double = when {
        constrainedTv -> 0.10
        totalRamMb <= 3072L -> 0.15
        else -> 0.20
    }
}
