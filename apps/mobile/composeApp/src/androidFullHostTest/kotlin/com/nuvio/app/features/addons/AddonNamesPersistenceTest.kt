package com.nuvio.app.features.addons

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddonNamesPersistenceTest {
    @Test fun `personal names and disabled state survive repository restart`() {
        val context = RuntimeEnvironment.getApplication()
        com.nuvio.app.features.profiles.ProfileRepository.clearInMemory()
        AddonStorage.initialize(context)
        val url = "https://addon.example/config%2FA/manifest.json?lang=fr"
        val name = "Mes films — famille\nFrançais"
        try {
            AddonStorage.saveInstalledAddonUrls(1, listOf(url))
            AddonStorage.saveAddonEnabledStates(1, mapOf(url to false))
            AddonStorage.saveAddonNames(1, mapOf(url to name))
            AddonStorage.saveAddonNames(2, mapOf(url to "Autre profil"))
            repeat(2) {
                AddonRepository.clearLocalState()
                AddonStorage.initialize(context)
                AddonRepository.initialize()
                val restored = AddonRepository.uiState.value.addons.single()
                assertEquals(url, restored.manifestUrl)
                assertEquals(name, restored.userSetName)
                assertFalse(restored.enabled)
                assertEquals("Autre profil", AddonStorage.loadAddonNames(2)[url])
            }
        } finally {
            AddonRepository.clearLocalState()
            AddonStorage.saveInstalledAddonUrls(1, emptyList())
            AddonStorage.saveAddonEnabledStates(1, emptyMap())
            AddonStorage.saveAddonNames(1, emptyMap())
            AddonStorage.saveAddonNames(2, emptyMap())
        }
    }
}
