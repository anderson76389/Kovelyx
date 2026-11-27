package com.kovelyx.maintenance.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.TimeSource

/** Only periodic pulls and position ticks share this window. Explicit edits bypass it. */
class PlaybackSyncCadence {
    private val origin = TimeSource.Monotonic.markNow()
    private data class Checkpoint(val owner: String, val profile: Int, val at: Long)
    private val last = MutableStateFlow<Checkpoint?>(null)

    fun admit(owner: String, profile: Int, playing: Boolean, now: Long = origin.elapsedNow().inWholeMilliseconds): Boolean {
        if (!playing) return true
        while (true) {
            val previous = last.value
            if (previous != null && previous.owner == owner && previous.profile == profile &&
                now - previous.at < 600_000L) return false
            if (last.compareAndSet(previous, Checkpoint(owner, profile, now))) return true
        }
    }
}
