package com.nuvio.app.features.debrid

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebridProviderTest {
    @Test fun premiumizeIsRetiredEvenWithAnOldStoredCredential() {
        kotlin.test.assertNull(DebridProviders.byId("premiumize"))
        assertTrue(DebridProviders.configuredServices(DebridSettings(providerApiKeys = mapOf("premiumize" to "old"))).isEmpty())
    }

    @Test fun `provider order starts with AllDebrid and Real Debrid before Torbox`() {
        kotlin.test.assertEquals(listOf("alldebrid", "realdebrid", "torbox"), DebridProviders.visible().map { it.id })
        assertTrue(DebridProviders.AllDebrid.supports(DebridProviderCapability.LocalTorrentResolve))
    }

    @Test
    fun `torbox exposes local addon capabilities`() {
        assertTrue(DebridProviders.Torbox.authMethod == DebridProviderAuthMethod.DeviceCode)
        assertTrue(DebridProviders.Torbox.supports(DebridProviderCapability.ClientResolve))
        assertTrue(DebridProviders.Torbox.supports(DebridProviderCapability.LocalTorrentCacheCheck))
        assertTrue(DebridProviders.Torbox.supports(DebridProviderCapability.LocalTorrentResolve))
        assertTrue(DebridProviders.Torbox.supports(DebridProviderCapability.CloudLibrary))
    }

    @Test
    fun `premiumize exposes oauth and cloud service capabilities`() {
        assertTrue(DebridProviders.Premiumize.visibleInUi)
        assertTrue(DebridProviders.Premiumize.authMethod == DebridProviderAuthMethod.DeviceCode)
        assertTrue(DebridProviders.Premiumize.supports(DebridProviderCapability.ClientResolve))
        assertTrue(DebridProviders.Premiumize.supports(DebridProviderCapability.LocalTorrentCacheCheck))
        assertTrue(DebridProviders.Premiumize.supports(DebridProviderCapability.LocalTorrentResolve))
        assertTrue(DebridProviders.Premiumize.supports(DebridProviderCapability.CloudLibrary))
    }

    @Test
    fun `real debrid is visible and resolves torrents without claiming cache or cloud support`() {
        assertTrue(DebridProviders.RealDebrid.authMethod == DebridProviderAuthMethod.ApiKey)
        assertTrue(DebridProviders.RealDebrid.visibleInUi)
        assertTrue(DebridProviders.RealDebrid.supports(DebridProviderCapability.ClientResolve))
        assertFalse(DebridProviders.RealDebrid.supports(DebridProviderCapability.LocalTorrentCacheCheck))
        assertTrue(DebridProviders.RealDebrid.supports(DebridProviderCapability.LocalTorrentResolve))
        assertFalse(DebridProviders.RealDebrid.supports(DebridProviderCapability.CloudLibrary))
    }
}
