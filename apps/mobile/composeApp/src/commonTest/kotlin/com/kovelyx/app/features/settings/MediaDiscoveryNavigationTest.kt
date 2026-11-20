package com.kovelyx.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Navigation regression for both Windows/Linux and Android phone/tablet. */
class MediaDiscoveryNavigationTest {
    private val playbackSubpages = listOf(
        SettingsPage.ContentPlayer,
        SettingsPage.ContentStreamSelection,
        SettingsPage.ContentUpNext,
        SettingsPage.ContentSkipSegments,
        SettingsPage.ContentPlayerInterface,
        SettingsPage.ContentAudio,
        SettingsPage.ContentSubtitles,
        SettingsPage.ContentVideo,
        SettingsPage.ContentP2P,
    )

    @Test
    fun allNinePlaybackScreensReallyLiveInsideContentDiscovery() {
        assertEquals(9, playbackSubpages.size)
        assertEquals(
            playbackSubpages,
            SettingsPage.entries.filter { it in playbackSubpages },
        )
        playbackSubpages.forEach { page ->
            assertEquals(SettingsPage.ContentDiscovery, page.parentPage)
            assertEquals(SettingsPage.ContentDiscovery, page.previousPage())
            assertEquals(SettingsCategory.Watch, page.category)
            assertTrue(page.opensInlineOnTablet)
        }
    }

    @Test
    fun oldSavedRoutesRemainValidAndExistingMediaServicesStayNested() {
        assertEquals(SettingsPage.Root, SettingsPage.Playback.previousPage())
        assertEquals(SettingsPage.Root, SettingsPage.ContentDiscovery.previousPage())
        assertEquals(SettingsPage.ContentDiscovery, SettingsPage.Addons.previousPage())
        assertEquals(SettingsPage.ContentDiscovery, SettingsPage.Plugins.previousPage())
        assertEquals(SettingsPage.ContentDiscovery, SettingsPage.MediaServers.previousPage())
        assertEquals(SettingsPage.MediaServers, SettingsPage.MediaServer.previousPage())
    }
}
