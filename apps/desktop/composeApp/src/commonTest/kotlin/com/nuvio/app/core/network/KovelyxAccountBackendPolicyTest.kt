package com.nuvio.app.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KovelyxAccountBackendPolicyTest {
    private fun server(url: String, key: String = "sb_publishable_KovelyxTest") =
        ServerConfiguration(
            backendUrl = url,
            publishableKey = key,
            capabilities = ServerCapabilities(emailPasswordAuth = true, tvLogin = false),
            isCustom = false,
        )

    @Test fun rejectsUnconfiguredAndNuvioAccountServers() {
        assertFalse(server("").isKovelyxAccountBackendReady())
        assertFalse(server("https://api.nuvio.tv").isKovelyxAccountBackendReady())
        assertFalse(server("https://auth.nuvio.app").isKovelyxAccountBackendReady())
        assertFalse(server("http://accounts.example.com").isKovelyxAccountBackendReady())
        assertFalse(server("https://kovelyx-project.supabase.co", "sb_secret_wrong").isKovelyxAccountBackendReady())
    }

    @Test fun acceptsOnlyExplicitIndependentSecureEndpoints() {
        assertTrue(server("https://kovelyx-project.supabase.co").isKovelyxAccountBackendReady())
        assertTrue(server("http://127.0.0.1:54321").isKovelyxAccountBackendReady())
        assertFalse(server("https://kovelyx-project.supabase.co", "").isKovelyxAccountBackendReady())
    }
}
