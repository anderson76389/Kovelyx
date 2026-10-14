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
    fun configuredAccountServerCanStillBeChecked() {
        assertTrue(shouldProbeKovelyxBackend(configuration("https://example.org", "non-empty-key")))
    }
}
