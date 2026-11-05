package com.nuvio.app.features.plugins

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonStorage
import com.russhwolf.settings.SettingsInitializer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Independent-cloud regression: missing remote state never erases offline plugins. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PluginSyncTest {
    private val server = MockWebServer()
    private val manifestUrl get() = server.url("/plugin/manifest.json").toString()

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        SettingsInitializer().create(context)
        PluginStorage.initialize(context)
        AddonStorage.initialize(context)
        PluginRepository.clearLocalState()
        AddonRepository.clearLocalState()
        server.start()
    }

    @After fun tearDown() {
        PluginRepository.clearLocalState()
        AddonRepository.clearLocalState()
        server.shutdown()
    }

    @Test fun cachedRepositoriesSurviveAnUnavailableRemote() = runBlocking {
        val state = PluginsUiState(
            repositories = listOf(PluginRepositoryItem(
                manifestUrl = manifestUrl, name = "Saved", lastUpdated = System.currentTimeMillis()
            ))
        )
        PluginStorage.saveState(1, Json.encodeToString(state.toStoredPluginsState()))
        PluginRepository.initialize()
        PluginRepository.pullFromServer(1)
        assertEquals(listOf(manifestUrl), PluginRepository.uiState.value.repositories.map { it.manifestUrl })
        PluginRepository.clearLocalState()
        PluginRepository.initialize()
        assertEquals(listOf(manifestUrl), PluginRepository.uiState.value.repositories.map { it.manifestUrl })
        assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
    }

    @Test fun cachedAddonsSurviveAnUnavailableRemote() = runBlocking {
        AddonStorage.saveInstalledAddonUrls(1, listOf(manifestUrl))
        AddonStorage.saveAddonEnabledStates(1, mapOf(manifestUrl to false))
        AddonStorage.markDefaultsInitialized(1)
        AddonRepository.initialize()
        AddonRepository.pullFromServer(1)
        assertEquals(listOf(manifestUrl), AddonRepository.uiState.value.addons.map { it.manifestUrl })
        assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
    }

    @Test fun addRepositoryDownloadsOnlyItsManifestAndCode() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"name":"Independent plugin","version":"1","scrapers":[{"id":"S","name":"Scraper","version":"1","filename":"Code.js"}]}"""
        ))
        server.enqueue(MockResponse().setBody("module.exports = {}"))
        assertTrue(PluginRepository.addRepository(manifestUrl) is AddPluginRepositoryResult.Success)
        assertEquals("GET", assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)).method)
        assertEquals("GET", assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)).method)
        assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
    }
}
