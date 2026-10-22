package com.nuvio.app.features.watchprogress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith

class CloudProgressStorageIsolationTest {
    @Test
    fun legacy_guest_data_is_preserved_and_cloud_accounts_are_isolated() {
        val legacy = "watch_progress_1"
        assertEquals(legacy, accountScopedProgressStorageKey(legacy, 1, null))
        assertEquals(legacy, accountScopedProgressStorageKey(legacy, 1, ""))
        val alice = accountScopedProgressStorageKey(legacy, 1, "user-A")
        val bob = accountScopedProgressStorageKey(legacy, 1, "user-B")
        assertNotEquals(legacy, alice)
        assertNotEquals(alice, bob)
        assertNotEquals(alice, accountScopedProgressStorageKey(legacy, 2, "user-A"))
        assertFailsWith<IllegalArgumentException> {
            accountScopedProgressStorageKey(legacy, 0, "user-A")
        }
    }
}
