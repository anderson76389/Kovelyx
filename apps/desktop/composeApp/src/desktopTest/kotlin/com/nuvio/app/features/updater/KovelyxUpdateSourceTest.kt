package com.nuvio.app.features.updater

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KovelyxUpdateSourceTest {
    @Test
    fun officialInstallerIsAccepted() {
        assertTrue(
            isOfficialKovelyxReleaseAsset(
                URI("https://github.com/anderson76389/Kovelyx/releases/download/v0.1.185-alpha/Kovelyx-Windows-Setup-v0.1.185-alpha.exe"),
                "Kovelyx-Windows-Setup-v0.1.185-alpha.exe",
            )
        )
    }

    @Test
    fun portableLinuxAndPackageFormatsAreAccepted() {
        listOf(
            "Kovelyx-Windows-Portable-v0.1.185-alpha.zip",
            "Kovelyx-Linux-x86_64-v0.1.185-alpha.AppImage",
            "Kovelyx-Linux-x86_64-v0.1.185-alpha.rpm",
            "Kovelyx-Linux-amd64-v0.1.185-alpha.deb",
        ).forEach { name ->
            assertTrue(
                isOfficialKovelyxReleaseAsset(
                    URI("https://github.com/anderson76389/Kovelyx/releases/download/v0.1.185-alpha/$name"),
                    name,
                )
            )
        }
    }

    @Test
    fun windowsUpdatesChooseMsiForOneClickInstallation() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
        val version = "v0.1.186-alpha"
        val selected = selectBestUpdateAsset(
            assets = listOf(
                AppUpdateAssetCandidate("Kovelyx-Windows-Setup-$version.exe", "https://example.test/setup.exe"),
                AppUpdateAssetCandidate("Kovelyx-Windows-$version.msi", "https://example.test/installer.msi"),
            ),
            selector = AppUpdaterPlatform.assetSelector,
        )
        assertEquals("Kovelyx-Windows-$version.msi", selected?.name)
    }

    @Test
    fun untrustedOrMismatchedDownloadsAreRejected() {
        val valid = "Kovelyx-Windows-Setup-v0.1.185-alpha.exe"
        listOf(
            "http://github.com/anderson76389/Kovelyx/releases/download/v0.1.185-alpha/$valid",
            "https://github.com.evil.test/anderson76389/Kovelyx/releases/download/v0.1.185-alpha/$valid",
            "https://github.com/other/Kovelyx/releases/download/v0.1.185-alpha/$valid",
            "https://github.com/anderson76389/Kovelyx/releases/download/v0.1.185-alpha/$valid?key=abc",
            "https://github.com/anderson76389/Kovelyx/releases/download/not-a-release/$valid",
        ).forEach { url ->
            assertFalse(isOfficialKovelyxReleaseAsset(URI(url), valid), url)
        }
        assertFalse(isOfficialKovelyxReleaseAsset(
            URI("https://github.com/anderson76389/Kovelyx/releases/download/v0.1.185-alpha/$valid"),
            "AnotherApp.exe",
        ))
    }

    @Test
    fun futureBuildFromSameRepositorySortsNewerThanExistingTestRelease() {
        assertTrue(VersionUtils.isRemoteNewer("v0.1.185-alpha", "0.1.30-alpha"))
        assertFalse(VersionUtils.isRemoteNewer("v0.1.185-alpha", "0.1.185-alpha"))
    }
}
