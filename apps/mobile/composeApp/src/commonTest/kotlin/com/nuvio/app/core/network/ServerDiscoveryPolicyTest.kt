package com.nuvio.app.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ServerDiscoveryPolicyTest {
    @Test
    fun `known Nuvio domains and all their subdomains are blocked`() {
        val addresses = listOf(
            "https://nuvio.tv",
            "https://api.nuvio.tv",
            "https://nuvio.tv:443/.well-known/nuvio",
            "https://api-two.nuvioapp.space",
            "https://nuvioapp.space",
            "https://api.nuvio.tv.",
            "https://A.B.API.NUVIO.TV:8443/link",
            "http://nuvioapp.space",
        )
        addresses.forEach { assertTrue(ServerDiscoveryPolicy.isOfficial(it), it) }
    }

    @Test
    fun `independent self hosted servers are not rejected by a substring`() {
        val addresses = listOf(
            "https://cloud.example.net",
            "https://nuvio.tv.example.net",
            "https://anothernuvio.tv",
            "https://nuvioapp.space.example.net",
            "https://api.kovelyx.example.org",
        )
        addresses.forEach { assertFalse(ServerDiscoveryPolicy.isOfficial(it), it) }
    }

    private fun document(url: String) = """
        {
          "version": 1,
          "service": "nuvio",
          "self_hosted": true,
          "backend_url": "$url",
          "publishable_key": "local-test-key",
          "capabilities": {"email_password_auth": true, "tv_login": false}
        }
    """.trimIndent()

    @Test
    fun `malicious discovery document cannot return old Nuvio infrastructure`() {
        val error = assertFailsWith<ServerDiscoveryException> {
            ServerDiscoveryPolicy.parse(
                "https://selfhosted.example/.well-known/nuvio",
                document("https://api-two.nuvioapp.space"),
            )
        }
        assertEquals(ServerDiscoveryFailure.OfficialServer, error.failure)
    }

    @Test
    fun `independent legacy compatible discovery document remains supported`() {
        val config = ServerDiscoveryPolicy.parse(
            "https://selfhosted.example/.well-known/nuvio",
            document("https://my-own-server.example"),
        )
        assertTrue(config.isCustom)
        assertEquals("https://my-own-server.example", config.backendUrl)
    }
}
