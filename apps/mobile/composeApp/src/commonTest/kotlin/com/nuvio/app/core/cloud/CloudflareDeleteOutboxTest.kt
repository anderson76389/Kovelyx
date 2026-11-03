package com.nuvio.app.core.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudflareDeleteOutboxTest {
    private fun change(owner: String = "owner", profile: String = "profile", key: String = "movie", nonce: Long = 1L) =
        PendingCloudflareDelete(owner, profile, 1, "watch_progress", key, 100L, nonce)

    @Test
    fun pendingDeletionSurvivesRecreationAndIsScopedToOwnerAndCloudProfile() {
        var stored: String? = null
        val outbox = CloudflareDeleteOutbox({ stored }, { stored = it })
        val first = change()
        outbox.record(first)
        outbox.record(change(owner = "another"))
        outbox.record(change(profile = "another-profile"))
        val restored = CloudflareDeleteOutbox({ stored }, { stored = it })
        assertEquals(listOf(first), restored.pending("owner", "profile"))
        assertTrue(restored.pending("unknown", "profile").isEmpty())
        restored.acknowledge(first)
        assertTrue(restored.pending("owner", "profile").isEmpty())
        assertEquals(1, restored.pending("another", "profile").size)
        assertEquals(1, restored.pending("owner", "another-profile").size)
    }

    @Test
    fun oldNetworkResponseCannotClearADeletionQueuedLaterForTheSameItem() {
        var stored: String? = null
        val outbox = CloudflareDeleteOutbox({ stored }, { stored = it })
        val first = change(nonce = 1L)
        val newer = change(nonce = 2L)
        outbox.record(first)
        outbox.record(newer)
        outbox.acknowledge(first)
        assertEquals(listOf(newer), outbox.pending("owner", "profile"))
        outbox.acknowledge(newer)
        assertTrue(outbox.pending("owner", "profile").isEmpty())
    }
}
