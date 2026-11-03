package com.kovelyx.cloud.addons

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class ConfiguredAddonSyncTest {
    private val retiredHash = "2e941e9a17d4254f4fe526597355fb42781e5d12aece5ac78682f1158bb655d8"
    private val policy = AddonDefaultsPolicy { if (it == "retired.example") retiredHash else it }
    private val configured = listOf(
        CloudAddon("https://aio.example/Config%2FAbC/manifest.json?key=Test%2BOnly%3D&lang=fr", "AIO Metadata", true),
        CloudAddon("https://streamfusion.example/config/AbCDef/manifest.json?token=Test%2FOnly&path=/", "StreamFusion", false),
        CloudAddon("https://comet.example/eyJrZXkiOiJURVNUIn0=/manifest.json", "Comet", true),
    )
    private var cloud: CloudAddonRow? = null
    private var writes = 0
    private var loseReply = false
    private inner class Device(initial: List<CloudAddon> = listOf(CloudAddon(OPENSUBTITLES_V3_MANIFEST))) {
        var items = initial
        val state = mutableMapOf<String, String>()
        val engine = CloudAddonEngine({ state[it] }, { key, value -> state[key] = value }, policy)
        fun edit(next: List<CloudAddon>) { items = next; engine.edited("owner.profile", next) }
        suspend fun sync(): Boolean {
            val before = items
            return engine.synchronize("owner.profile", before, cloud, write = { value, revision ->
                assertEquals(cloud?.revision ?: 0, revision)
                cloud = CloudAddonRow(revision + 1, value, ADDON_DEFAULTS_VERSION)
                writes++
                if (loseReply) { loseReply = false; error("response lost after publication") }
            }, apply = { value -> if (items != before) false else { items = value; true } })
        }
        suspend fun settle() { repeat(4) { if (sync()) return }; error("sync still pending") }
    }
    @Test fun opaqueConfigurationSurvivesThreeDifferentClientsAndRemovalsDoNotReturn() = runTest {
        cloud = CloudAddonRow(7, configured + CloudAddon("https://retired.example/manifest.json"))
        val windows = Device(); windows.settle()
        val mobile = Device(); mobile.settle()
        val television = Device(); television.settle()
        val expected = configured + CloudAddon(OPENSUBTITLES_V3_MANIFEST)
        assertEquals(expected, windows.items); assertEquals(expected, mobile.items); assertEquals(expected, television.items)
        val changed = listOf(expected[2], expected[1].copy(enabled = true, name = "Fusion FR"), expected[0], expected[3])
        mobile.edit(changed); mobile.settle(); windows.settle(); television.settle()
        assertEquals(changed, windows.items); assertEquals(changed, television.items)
        val before = writes; television.settle(); assertEquals(before, writes)
        television.edit(emptyList()); television.settle(); windows.settle(); mobile.settle()
        assertEquals(emptyList<CloudAddon>(), windows.items); assertEquals(emptyList<CloudAddon>(), mobile.items)
        val freshDevice = Device(); freshDevice.settle(); assertEquals(emptyList<CloudAddon>(), freshDevice.items)
    }
    @Test fun firstLocalInstallKeepsProvidersAlreadyInstalledOnAnotherDevice() = runTest {
        cloud = CloudAddonRow(4, configured.take(2), ADDON_DEFAULTS_VERSION)
        val device = Device()
        device.edit(device.items + configured[2])
        device.settle()
        assertEquals(setOf(OPENSUBTITLES_V3_MANIFEST, configured[0].url, configured[1].url, configured[2].url), device.items.map { it.url }.toSet())
    }
    @Test fun lostFirstMigrationReplyCannotEraseRemoteConfigurationOrResurrectADeletion() = runTest {
        cloud = CloudAddonRow(5, configured)
        val device = Device(); loseReply = true
        var failed = false
        try { device.sync() } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(configured + CloudAddon(OPENSUBTITLES_V3_MANIFEST), device.items)
        device.edit(device.items.filterNot { it.url == configured[1].url })
        device.settle()
        assertEquals(device.items, cloud!!.items)
        assertFalse(cloud!!.items.any { it.url == configured[1].url })
    }
    @Test fun canonicalUrlsKeepCaseSensitiveTokensAndTheEntireQuery() {
        assertEquals("https://addon.example/Config" to "?key=AbC%2Ftest&path=/", splitConfiguredAddonBaseUrl("https://addon.example/Config/?key=AbC%2Ftest&path=/"))
        val mixed = "https://STREAMFUSION.example/Config/AbC/manifest.json?key=aB%2Fcd%2B%3D&path=/"
        assertEquals("https://streamfusion.example/Config/AbC/manifest.json?key=aB%2Fcd%2B%3D&path=/", canonicalAddonManifestUrl(mixed))
        assertEquals("https://comet.example/Config/AbC/manifest.json?key=XyZ", canonicalAddonManifestUrl("stremio://COMET.example/Config/AbC?key=XyZ"))
        assertFalse(canonicalAddonManifestUrl(mixed) == canonicalAddonManifestUrl(mixed.replace("AbC", "abc")))
        assertEquals(OPENSUBTITLES_V3_MANIFEST, canonicalAddonManifestUrl("opensubtitles-v3.strem.io"))
    }
    @Test fun disabledOrConfiguredSubtitleAddonIsNotDuplicatedOrReenabled() {
        val configured = CloudAddon(OPENSUBTITLES_V3_MANIFEST + "?lang=fr", "Mes sous-titres", false)
        assertEquals(listOf(configured), policy.seed(listOf(configured)))
        assertEquals(emptyList<CloudAddon>(), policy.clean(listOf(CloudAddon("https://retired.example/manifest.json"))))
        assertEquals(listOf(CloudAddon(OPENSUBTITLES_V3_MANIFEST)), policy.seed(emptyList()))
    }
    @Test fun aLargeValidConfigurationUrlIsNotTruncated() = runTest {
        val long = CloudAddon("https://aio.example/" + "AbC".repeat(2500) + "/manifest.json?lang=fr", "AIO configuré")
        val first = Device(); first.edit(listOf(long)); first.settle()
        val other = Device(); other.settle(); assertTrue(other.items.any { it.url == long.url })
    }
}
