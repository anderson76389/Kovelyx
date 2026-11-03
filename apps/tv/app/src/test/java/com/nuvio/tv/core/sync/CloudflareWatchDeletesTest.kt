package com.nuvio.tv.core.sync

import com.nuvio.tv.domain.model.WatchedMutationKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudflareWatchDeletesTest {
    @Test
    fun `deletes use current cloud revisions and are acknowledged only after success`() = runTest {
        val store = mockk<WatchStateMutationStore>(relaxed = true)
        val pending = WatchedMutationKey("show", 1, 2)
        coEvery { store.pendingProgressDeletes(4) } returns setOf("show_s1e2")
        coEvery { store.pendingWatchedDeletes(4) } returns setOf(pending)
        val calls = mutableListOf<Triple<String, String, Int>>()
        val result = flushCloudflareWatchDeletes(4, store,
            mapOf("show_s1e2" to CloudflareDeleteRow("show_s1e2", 7, false)),
            listOf(CloudflareDeleteRow("series:show:1:2", 3, false, "show", 1, 2),
                   CloudflareDeleteRow("series:show:1:3", 8, false, "show", 1, 3))) { cat, key, rev ->
            calls += Triple(cat, key, rev)
        }
        assertEquals(listOf(Triple("watch_progress", "show_s1e2", 7),
                            Triple("watched_items", "series:show:1:2", 3)), calls)
        assertEquals(setOf("show_s1e2"), result.progress)
        assertEquals(setOf("series:show:1:2"), result.watched)
        coVerify { store.acknowledgeProgressDeletes(listOf("show_s1e2"), 4) }
        coVerify { store.acknowledgeWatchedDeletes(listOf(pending), 4) }
        coVerify(exactly = 0) { store.pendingProgressDeletes(1) }
    }

    @Test
    fun `offline or conflict keeps the failed deletion pending for another pull`() = runTest {
        val store = mockk<WatchStateMutationStore>(relaxed = true)
        coEvery { store.pendingProgressDeletes(2) } returns setOf("movie")
        val failure = runCatching {
            flushCloudflareWatchDeletes(2, store, emptyMap(), emptyList()) { _, _, _ ->
                error("HTTP 409 or offline")
            }
        }
        assertTrue(failure.isFailure)
        coVerify(exactly = 0) { store.acknowledgeProgressDeletes(any(), any()) }
    }

    @Test
    fun `already deleted rows need no duplicate write and whole-show removal covers its episodes`() = runTest {
        val store = mockk<WatchStateMutationStore>(relaxed = true)
        coEvery { store.pendingProgressDeletes(1) } returns setOf("movie")
        coEvery { store.pendingWatchedDeletes(1) } returns setOf(WatchedMutationKey("show", null, null))
        val calls = mutableListOf<String>()
        flushCloudflareWatchDeletes(1, store,
            mapOf("movie" to CloudflareDeleteRow("movie", 4, true)),
            listOf(CloudflareDeleteRow("series:show:1:1", 2, false, "show", 1, 1),
                   CloudflareDeleteRow("series:other:1:1", 2, false, "other", 1, 1),
                   CloudflareDeleteRow("series:show:1:2", 9, true, "show", 1, 2))) { _, key, _ ->
            calls += key
        }
        assertEquals(listOf("series:show:1:1"), calls)
    }
}
