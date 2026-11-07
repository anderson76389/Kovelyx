package com.nuvio.tv.data.remote

import com.nuvio.tv.domain.model.isPublicServerHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDiscoveryPolicyTest {
    @Test
    fun discoveryUrlAddsSchemeAndKnownPath() {
        assertEquals(
            "https://backend.example.com/.well-known/kovelyx",
            ServerDiscoveryPolicy.discoveryUrl("backend.example.com")
        )
    }

    @Test
    fun discoveryUrlPreservesBackendPathAndIsIdempotent() {
        assertEquals(
            "https://example.com/backend/.well-known/kovelyx",
            ServerDiscoveryPolicy.discoveryUrl("https://example.com/backend/.well-known/kovelyx")
        )
    }

    @Test
    fun discoveryUrlUpgradesPastedLegacyPathWithoutContactingIt() {
        assertEquals(
            "https://example.com/backend/.well-known/kovelyx",
            ServerDiscoveryPolicy.discoveryUrl("https://example.com/backend/.well-known/nuvio")
        )
    }

    @Test
    fun parseRejectsLegacyNuvioServiceEvenOnAnIndependentHost() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                "https://example.com/.well-known/kovelyx",
                """
                    {
                      "version": 1,
                      "service": "nuvio",
                      "self_hosted": true,
                      "backend_url": "https://backend.example.com",
                      "publishable_key": "public-key",
                      "capabilities": { "email_password_auth": true }
                    }
                """.trimIndent()
            )
        }
        assertEquals(ServerDiscoveryFailure.WRONG_SERVICE, error.failure)
    }

    @Test
    fun parseAcceptsVersionOneDocument() {
        val configuration = ServerDiscoveryPolicy.parse(
            discoveryUrl = "https://example.com/.well-known/kovelyx",
            document = """
                {
                  "version": 1,
                  "service": "kovelyx",
                  "self_hosted": true,
                  "backend_url": "https://backend.example.com/",
                  "publishable_key": "public-key",
                  "capabilities": {
                    "email_password_auth": true,
                    "tv_login": true
                  }
                }
            """.trimIndent()
        )

        assertEquals("https://backend.example.com", configuration.backendUrl)
        assertEquals("public-key", configuration.publishableKey)
        assertTrue(configuration.isCustom)
        assertTrue(configuration.capabilities.emailPasswordAuth)
        assertTrue(configuration.capabilities.tvLogin)
    }

    @Test
    fun parseRejectsUnsupportedVersion() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                discoveryUrl = "https://example.com/.well-known/kovelyx",
                document = """
                    {
                      "version": 2,
                      "service": "kovelyx",
                      "self_hosted": true,
                      "backend_url": "https://backend.example.com",
                      "publishable_key": "public-key",
                      "capabilities": { "email_password_auth": true }
                    }
                """.trimIndent()
            )
        }

        assertEquals(ServerDiscoveryFailure.UNSUPPORTED_VERSION, error.failure)
    }

    @Test
    fun parseRejectsDocumentWithoutSupportedAuthentication() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                discoveryUrl = "https://example.com/.well-known/kovelyx",
                document = """
                    {
                      "version": 1,
                      "service": "kovelyx",
                      "self_hosted": true,
                      "backend_url": "https://backend.example.com",
                      "publishable_key": "public-key",
                      "capabilities": {}
                    }
                """.trimIndent()
            )
        }

        assertEquals(ServerDiscoveryFailure.NO_SUPPORTED_AUTH, error.failure)
    }

    @Test
    fun publicHostClassificationDistinguishesPrivateNetworks() {
        assertFalse(isPublicServerHost("http://192.168.1.10:8000"))
        assertFalse(isPublicServerHost("http://172.20.0.5"))
        assertFalse(isPublicServerHost("http://server.local"))
        assertTrue(isPublicServerHost("https://backend.example.com"))
    }

    @Test
    fun matchesOfficialBackendByHostAndPort() {
        assertTrue(
            ServerDiscoveryPolicy.matchesBackend(
                "https://api.nuvio.tv/.well-known/kovelyx",
                "https://api.nuvio.tv"
            )
        )
        assertFalse(
            ServerDiscoveryPolicy.matchesBackend(
                "https://api.nuvio.tv.example.com/.well-known/kovelyx",
                "https://api.nuvio.tv"
            )
        )
    }
    @Test
    fun retiredNuvioHostsAreBlockedByWholeDnsLabel() {
        listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://api-two.nuvioapp.space",
            "http://nuvioapp.space:8080/.well-known/kovelyx",
            "https://A.B.API.NUVIO.TV:8443/link"
        ).forEach { assertTrue(it, ServerDiscoveryPolicy.isRetiredUpstream(it)) }

        listOf(
            "https://my-cloudflare-worker.workers.dev",
            "https://nuvio.tv.example.org",
            "https://nuvioapp.space.example.net",
            "https://anothernuvio.tv"
        ).forEach { assertFalse(it, ServerDiscoveryPolicy.isRetiredUpstream(it)) }
    }

    @Test
    fun discoveryCannotSwitchAnIndependentServerToRetiredNuvioBackend() {
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                "https://my-own-host.example/.well-known/kovelyx",
                """
                    {
                      "version": 1,
                      "service": "kovelyx",
                      "self_hosted": true,
                      "backend_url": "https://api-two.nuvioapp.space",
                      "publishable_key": "public-test-key",
                      "capabilities": { "email_password_auth": true }
                    }
                """.trimIndent()
            )
        }
        assertEquals(ServerDiscoveryFailure.OFFICIAL_SERVER, error.failure)
    }

}
