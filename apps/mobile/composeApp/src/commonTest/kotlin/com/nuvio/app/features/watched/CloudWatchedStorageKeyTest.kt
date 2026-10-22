package com.nuvio.app.features.watched

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CloudWatchedStorageKeyTest {
    @Test fun guestsKeepTheirOriginalHistoryStorageKey() {
        assertEquals(
            "watched_payload_1",
            accountScopedWatchedStorageKey("watched_payload_1", 1, null)
        )
    }

    @Test fun accountsAndProfilesAreIsolated() {
        val alice = accountScopedWatchedStorageKey("watched_payload_1", 1, "alice")
        val bob = accountScopedWatchedStorageKey("watched_payload_1", 1, "bob")
        val aliceProfile2 = accountScopedWatchedStorageKey("watched_payload_2", 2, "alice")
        assertNotEquals("watched_payload_1", alice)
        assertNotEquals(alice, bob)
        assertNotEquals(alice, aliceProfile2)
        assertEquals(alice, accountScopedWatchedStorageKey("watched_payload_1", 1, "alice"))
    }
}
