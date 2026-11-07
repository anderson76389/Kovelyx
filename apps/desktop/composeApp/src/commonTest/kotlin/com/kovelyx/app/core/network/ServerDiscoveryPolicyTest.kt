package com.kovelyx.app.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServerDiscoveryPolicyTest {
    @Test
    fun retiredUpstreamDomainsAreBlocked() {
        listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://nuvioapp.space",
            "https://api-two.nuvioapp.space",
            "https://A.B.API.NUVIO.TV:8443/link"
        ).forEach { assertTrue(ServerDiscoveryPolicy.isOfficial(it), it) }
    }

    @Test
    fun independentServersAreNotRejectedBySubstring() {
        listOf(
            "https://my-own-worker.workers.dev",
            "https://nuvio.tv.example.net",
            "https://nuvioapp.space.example.org",
            "https://anothernuvio.tv"
        ).forEach { assertFalse(ServerDiscoveryPolicy.isOfficial(it), it) }
    }

    @Test
    fun maliciousDiscoveryDocumentCannotPointBackToRetiredBackend() {
        val payload = """
            {
              "version": 1,
              "service": "kovelyx",
              "self_hosted": true,
              "backend_url": "https://api-two.nuvioapp.space",
              "publishable_key": "local-test-key",
              "capabilities": { "email_password_auth": true }
            }
        """.trimIndent()
        val error = assertFailsWith<ServerDiscoveryException> {
            ServerDiscoveryPolicy.parse(
                "https://independent.example/.well-known/kovelyx",
                payload
            )
        }
        assertEquals(ServerDiscoveryFailure.OfficialServer, error.failure)
    }
}
