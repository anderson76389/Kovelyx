package com.kovelyx.tv.core.sync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCredentialModelsTest {
    @Test
    fun completeRemoteSnapshotDoesNotRequireSeeding() {
        val snapshot = ProviderCredentialSnapshot(
            profileId = 1,
            values = listOf(
                ProviderCredentialValue("debrid:torbox", "api_key", "local-torbox"),
                ProviderCredentialValue("animeskip", "client_id", "local-anime")
            )
        )
        val rows = listOf(
            RemoteProviderCredential(
                provider = "DEBRID:TORBOX",
                credentialJson = buildJsonObject { put("api_key", "remote") }
            ),
            RemoteProviderCredential(
                provider = "animeskip",
                credentialJson = buildJsonObject { put("client_id", "remote") }
            )
        )

        assertFalse(shouldSeedProviderCredentials(snapshot, rows))
    }

    @Test
    fun missingRemoteProviderRequiresSeeding() {
        val snapshot = ProviderCredentialSnapshot(
            profileId = 1,
            values = listOf(
                ProviderCredentialValue("debrid:torbox", "api_key", "local-torbox"),
                ProviderCredentialValue("animeskip", "client_id", "local-anime")
            )
        )
        val rows = listOf(
            RemoteProviderCredential(
                provider = "debrid:torbox",
                credentialJson = buildJsonObject { put("api_key", "remote") }
            )
        )

        assertTrue(shouldSeedProviderCredentials(snapshot, rows))
    }

    @Test
    fun `remote values replace only supported local providers`() {
        val local = ProviderCredentialSnapshot(
            profileId = 2,
            values = listOf(
                ProviderCredentialValue("debrid:torbox", "api_key", "local-torbox"),
                ProviderCredentialValue("animeskip", "client_id", "local-anime")
            )
        )
        val remote = listOf(
            RemoteProviderCredential(
                provider = "debrid:torbox",
                credentialJson = buildJsonObject { put("api_key", "remote-torbox") }
            ),
            RemoteProviderCredential(
                provider = "tmdb",
                credentialJson = buildJsonObject { put("api_key", "remote-tmdb") }
            )
        )

        val merged = local.mergeRemote(remote)

        assertEquals("remote-torbox", merged.values[0].value)
        assertEquals("local-anime", merged.values[1].value)
    }

    @Test
    fun `blank remote value is retained as a clear tombstone`() {
        val local = ProviderCredentialSnapshot(
            profileId = 1,
            values = listOf(ProviderCredentialValue("mdblist", "api_key", "local"))
        )
        val remote = listOf(
            RemoteProviderCredential(
                provider = "mdblist",
                credentialJson = buildJsonObject { put("api_key", "") }
            )
        )

        assertEquals("", local.mergeRemote(remote).values.single().value)
    }
}
