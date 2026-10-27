package com.nuvio.tv.core.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingProviderRegistryTest {
    @Test
    fun `SIMKL providers are excluded and Trakt stays available`() {
        val simkl = FakeProvider(TrackingProviderId.SIMKL, authenticated = true, scrobble = true)
        val trakt = FakeProvider(TrackingProviderId.TRAKT, authenticated = false, scrobble = true)
        val registry = TrackingProviderRegistry(listOf(simkl, trakt))

        assertEquals(listOf(TrackingProviderId.TRAKT), registry.providers().map { it.descriptor.id })
        assertEquals(emptySet<TrackingProviderId>(), registry.connectedProviderIds())
        assertEquals(emptyList<TrackingProviderId>(), registry.connectedScrobblers().map { it.providerId })
        assertFalse(registry.isAuthenticated(TrackingProviderId.TRAKT))
        assertFalse(registry.isAuthenticated(TrackingProviderId.SIMKL))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `registry rejects duplicate provider ids`() {
        TrackingProviderRegistry(
            listOf(
                FakeProvider(TrackingProviderId.TRAKT, authenticated = true),
                FakeProvider(TrackingProviderId.TRAKT, authenticated = false)
            )
        )
    }

    private class FakeProvider(
        id: TrackingProviderId,
        authenticated: Boolean,
        scrobble: Boolean = false
    ) : TrackingProvider {
        override val descriptor = TrackingProviderDescriptor(
            id = id,
            displayName = id.storageId,
            capabilities = if (scrobble) setOf(TrackingCapability.SCROBBLE) else emptySet()
        )
        override val isAuthenticated = MutableStateFlow(authenticated)
        override val scrobbler = if (scrobble) object : TrackingScrobbler {
            override val providerId = id
            override suspend fun scrobble(action: TrackingScrobbleAction, event: TrackingScrobbleEvent) = Unit
        } else {
            null
        }
    }
}
