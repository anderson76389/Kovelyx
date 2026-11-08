package com.kovelyx.tv.core.sync

import com.kovelyx.tv.core.auth.CloudflareTvAccountClient
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * TV-specific invariants: a user must explicitly select both a target
 * and a type of data. Never permit cross-account or self-profile writes.
 */
class CloudflareTvProfileSharingSecurityTest {
    private val primary = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val secondary = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val outsider = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    @Test fun no_category_no_remote_request() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                CloudflareTvProfileSharing.share(primary, setOf(secondary), false, false)
            }
        }
    }

    @Test fun no_target_no_remote_request() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                CloudflareTvProfileSharing.share(primary, emptySet(), true, true)
            }
        }
    }

    @Test fun self_profile_is_never_copied() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                CloudflareTvProfileSharing.share(primary, setOf(primary), true, true)
            }
        }
    }

    @Test fun account_cannot_copy_to_foreign_profile() = runTest {
        mockkObject(CloudflareTvAccountClient)
        try {
            every { CloudflareTvAccountClient.account } returns
                CloudflareTvAccountClient.Account("owner", "owner@example.test", "test-bearer")
            coEvery { CloudflareTvAccountClient.cloudProfiles() } returns listOf(
                primary to "Principal", secondary to "Famille"
            )
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    CloudflareTvProfileSharing.share(primary, setOf(outsider), true, true)
                }
            }
            coVerify(exactly = 0) { CloudflareTvAccountClient.syncRows(any(), any(), any()) }
            coVerify(exactly = 0) { CloudflareTvAccountClient.writeSync(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { CloudflareTvAccountClient.publishCatalogueHead(any(), any(), any()) }
        } finally {
            unmockkObject(CloudflareTvAccountClient)
        }
    }

    @Test fun logged_out_account_never_reads_profile_data() = runTest {
        mockkObject(CloudflareTvAccountClient)
        try {
            every { CloudflareTvAccountClient.account } returns null
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking {
                    CloudflareTvProfileSharing.share(primary, setOf(secondary), true, true)
                }
            }
            coVerify(exactly = 0) { CloudflareTvAccountClient.cloudProfiles() }
            coVerify(exactly = 0) { CloudflareTvAccountClient.syncRows(any(), any(), any()) }
        } finally {
            unmockkObject(CloudflareTvAccountClient)
        }
    }
}
