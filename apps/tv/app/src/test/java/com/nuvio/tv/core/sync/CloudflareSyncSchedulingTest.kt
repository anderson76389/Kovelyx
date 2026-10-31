package com.nuvio.tv.core.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudflareSyncSchedulingTest {
    @Test
    fun `continuous playback uploads before the player becomes idle`() = runTest {
        val received = mutableListOf<Int>()
        backgroundScope.launch {
            flow {
                repeat(60) { position -> emit(position); delay(500L) }
            }.cloudflareSnapshotUpdates().collect { received += it }
        }
        runCurrent()
        advanceTimeBy(25_001L)
        runCurrent()
        assertEquals(2, received.size)
        assertTrue(received[0] >= 18)
        assertTrue(received[1] > received[0])
    }

    @Test
    fun `a burst is coalesced and identical snapshots do not cause echo requests`() = runTest {
        val received = mutableListOf<Int>()
        backgroundScope.launch {
            flow {
                repeat(100) { emit(it) }
                repeat(30) { emit(99); delay(1_000L) }
            }.cloudflareSnapshotUpdates().collect { received += it }
        }
        runCurrent()
        advanceTimeBy(25_001L)
        runCurrent()
        assertEquals(listOf(99), received)
    }
}
