package com.nuvio.app.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KovelyxBackendProbeTest {
    private fun configuration(url: String, key: String) = ServerConfiguration(
        backendUrl = url,
        publishableKey = key,
        capabilities = ServerCapabilities(emailPasswordAuth = false, tvLogin = false),
        isCustom = false,
    )

    @Test
    fun noAccountServerDoesNotDisableOnlineMetadata() {
        assertFalse(shouldProbeKovelyxBackend(configuration("", "")))
        assertFalse(shouldProbeKovelyxBackend(configuration("https://example.org", "")))
        assertFalse(shouldProbeKovelyxBackend(configuration("", "non-empty-key")))
    }

    @Test
    fun retiredNuvioHostsAreRejectedWithoutBlockingLookalikeOrSelfHostedDomains() {
        listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://sub.api.nuvio.tv:8443/profile",
            "https://nuvioapp.space",
            "https://api.nuvioapp.space",
            "https://API.NUVIO.TV./v1",
        ).forEach { assertTrue(ServerDiscoveryPolicy.isOfficial(it), it) }

        listOf(
            "https://example.net",
            "https://nuvio.tv.example.net",
            "https://notnuvio.tv",
            "https://nuvioapp.space.example.net",
            "http://192.168.1.20:8080",
        ).forEach { assertFalse(ServerDiscoveryPolicy.isOfficial(it), it) }
    }

    @Test
    fun customServerRepositoryRejectsRetiredNuvioAddressBeforePersisting() {
        val config = ServerConfiguration(
            backendUrl = "https://api.nuvio.tv",
            publishableKey = "test-public-key",
            capabilities = ServerCapabilities(emailPasswordAuth = true, tvLogin = false),
            isCustom = true,
        )
        assertFalse(ServerConfigurationRepository.saveCustom(config))
    }

    @Test
    fun configuredAccountServerCanStillBeChecked() {
        assertTrue(shouldProbeKovelyxBackend(configuration("https://example.org", "non-empty-key")))
    }
}
