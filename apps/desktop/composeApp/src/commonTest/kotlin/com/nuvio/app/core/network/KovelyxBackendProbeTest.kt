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
    fun retiredNuvioDomainsAreBlockedButIndependentServersRemainAllowed() {
        for (host in listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://api.nuvio.tv.",
            "https://a.b.api.nuvio.tv:8443/link",
            "https://nuvioapp.space",
            "https://api.nuvioapp.space",
        )) {
            assertTrue(ServerDiscoveryPolicy.isOfficial(host), host)
        }
        for (host in listOf(
            "https://kovelyx.example.org",
            "https://nuvio.tv.example.org",
            "https://anothernuvio.tv",
            "https://api.kovelyx.example.org",
        )) {
            assertFalse(ServerDiscoveryPolicy.isOfficial(host), host)
        }
    }

    @Test
    fun selfHostedDiscoveryCannotRedirectAccountCreationIntoRetiredNuvio() {
        val json = """
            {
              "version": 1,
              "service": "nuvio",
              "self_hosted": true,
              "backend_url": "https://api.nuvioapp.space",
              "publishable_key": "test-key",
              "capabilities": {"email_password_auth": true}
            }
        """.trimIndent()
        kotlin.test.assertFailsWith<ServerDiscoveryException> {
            ServerDiscoveryPolicy.parse("https://independent.example/.well-known/nuvio", json)
        }
    }

    @Test
    fun configuredAccountServerCanStillBeChecked() {
        assertTrue(shouldProbeKovelyxBackend(configuration("https://example.org", "non-empty-key")))
    }
}
