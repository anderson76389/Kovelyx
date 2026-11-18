package com.kovelyx.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsNavigationCompatibilityTest {
    @Test fun allPagesCanReturnToRootWithoutCycles() {
        for (page in SettingsPage.entries) {
            val visited = mutableSetOf<SettingsPage>()
            var current: SettingsPage? = page
            while (current != null && current != SettingsPage.Root) {
                assertTrue(visited.add(current), "Cycle from $page")
                current = current.previousPage()
            }
            assertEquals(SettingsPage.Root, current)
        }
        assertNull(SettingsPage.Root.previousPage())
    }
    @Test fun savedRouteNamesRemainRecognized() {
        for (name in listOf("Account", "Appearance", "Playback", "Advanced", "TraktAuthentication", "ContentDiscovery", "Integrations", "Streams", "MediaServers", "MediaServer")) {
            assertEquals(name, SettingsPage.valueOf(name).name)
        }
        for (name in listOf("You", "Look", "Watch", "Services", "System")) {
            assertEquals(name, SettingsCategory.valueOf(name).name)
        }
        assertEquals(SettingsCategory.You, settingsCategoryForName("Account"))
        assertEquals(SettingsCategory.Look, settingsCategoryForName("General"))
        assertEquals(SettingsCategory.System, settingsCategoryForName("Advanced"))
        assertEquals(SettingsCategory.System, settingsCategoryForName("About"))
        assertEquals(SettingsCategory.Look, settingsCategoryForName("unknown"))
        assertEquals(SettingsPage.Layout, SettingsPage.Homescreen.previousPage())
        assertEquals(SettingsPage.Layout, SettingsPage.ContinueWatching.previousPage())
    }
    @Test fun serverDetailsReturnThroughTheSourceHub() {
        assertEquals(SettingsPage.MediaServers, SettingsPage.MediaServer.previousPage())
        assertEquals(SettingsPage.ContentDiscovery, SettingsPage.MediaServers.previousPage())
        assertEquals(SettingsPage.Root, SettingsPage.ContentDiscovery.previousPage())
    }
    @Test fun trackingAndMetadataKeepDistinctDestinationsInTheServicesArea() {
        assertEquals(SettingsCategory.Services, SettingsPage.TraktAuthentication.category)
        assertEquals(SettingsPage.Root, SettingsPage.TraktAuthentication.previousPage())
        assertEquals(SettingsPage.Integrations, SettingsPage.MdbListRatings.previousPage())
        assertEquals(SettingsPage.Integrations, SettingsPage.Debrid.previousPage())
    }
    @Test fun visualStreamSettingsKeepTheirAppearanceParent() {
        assertEquals(SettingsPage.Layout, SettingsPage.Streams.previousPage())
        assertEquals(SettingsCategory.Look, SettingsPage.Streams.category)
    }
}
