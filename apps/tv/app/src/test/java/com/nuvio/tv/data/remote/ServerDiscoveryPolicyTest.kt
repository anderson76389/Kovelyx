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
    fun blocksRetiredNuvioAndSubdomainsWithoutMatchingIndependentHosts() {
        for (url in listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://api.nuvio.tv.",
            "https://a.b.nuvio.tv:8443/",
            "https://nuvioapp.space",
            "https://api.nuvioapp.space",
        )) {
            assertTrue(url, ServerDiscoveryPolicy.isFormerNuvioService(url))
        }
        for (url in listOf(
            "https://cloud.example.org",
            "https://nuvio.tv.example.org",
            "https://anothernuvio.tv",
        )) {
            assertFalse(url, ServerDiscoveryPolicy.isFormerNuvioService(url))
        }
    }

    @Test
    fun rejectsDiscoveryManifestRedirectingToFormerNuvioAccountService() {
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
        val error = assertThrows(ServerDiscoveryException::class.java) {
            ServerDiscoveryPolicy.parse(
                discoveryUrl = "https://independent.example/.well-known/nuvio",
                document = json,
            )
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
