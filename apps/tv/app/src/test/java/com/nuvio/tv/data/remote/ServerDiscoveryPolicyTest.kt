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
            "https://backend.example.com/.well-known/nuvio",
            ServerDiscoveryPolicy.discoveryUrl("backend.example.com")
        )
    }

    @Test
    fun discoveryUrlPreservesBackendPathAndIsIdempotent() {
        assertEquals(
            "https://example.com/backend/.well-known/nuvio",
            ServerDiscoveryPolicy.discoveryUrl("https://example.com/backend/.well-known/nuvio")
        )
    }

    @Test
    fun parseAcceptsVersionOneDocument() {
        val configuration = ServerDiscoveryPolicy.parse(
            discoveryUrl = "https://example.com/.well-known/nuvio",
            document = """
                {
                  "version": 1,
                  "service": "nuvio",
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
                discoveryUrl = "https://example.com/.well-known/nuvio",
                document = """
                    {
                      "version": 2,
                      "service": "nuvio",
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
                discoveryUrl = "https://example.com/.well-known/nuvio",
                document = """
                    {
                      "version": 1,
                      "service": "nuvio",
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
    fun retiredNuvioHostsAreBlockedButUnrelatedSelfHostedDomainsAreAllowed() {
        listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://tv.api.nuvio.tv:443/backend",
            "https://nuvioapp.space",
            "https://api.nuvioapp.space",
            "https://API.NUVIO.TV./.well-known/nuvio"
        ).forEach { assertTrue(it, ServerDiscoveryPolicy.isRetiredUpstream(it)) }

        listOf(
            "https://nuvio.tv.example.org",
            "https://some-nuvio.tv",
            "https://nuvioapp.space.example.net",
            "https://backend.example.org",
            "http://192.168.1.10:8080"
        ).forEach { assertFalse(it, ServerDiscoveryPolicy.isRetiredUpstream(it)) }
    }

    @Test
    fun discoveryDocumentCannotRedirectUsToRetiredNuvioBackend() {
        val document = """
            {
              "version": 1, "service": "nuvio", "self_hosted": true,
              "backend_url": "https://api.nuvio.tv",
              "publishable_key": "test-public-key",
              "capabilities": {"email_password_auth": true}
            }
        """.trimIndent()
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse("https://example.org/.well-known/nuvio", document)
        }
        assertEquals(ServerDiscoveryFailure.OFFICIAL_SERVER, error.failure)
    }

    @Test
    fun matchesOfficialBackendByHostAndPort() {
        assertTrue(
            ServerDiscoveryPolicy.matchesBackend(
                "https://api.nuvio.tv/.well-known/nuvio",
                "https://api.nuvio.tv"
            )
        )
        assertFalse(
            ServerDiscoveryPolicy.matchesBackend(
                "https://api.nuvio.tv.example.com/.well-known/nuvio",
                "https://api.nuvio.tv"
            )
        )
    }
}
