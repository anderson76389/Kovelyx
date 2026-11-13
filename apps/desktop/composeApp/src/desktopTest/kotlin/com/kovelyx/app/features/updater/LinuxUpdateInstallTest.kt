package com.kovelyx.app.features.updater

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxUpdateInstallTest {

    // The asset names of a real release, in the order the GitHub API returns them.
    private val releaseAssets = listOf(
        asset("Kovelyx-Linux-x86_64-v1.1.1137.AppImage"),
        asset("Kovelyx-Linux-x86_64-v1.1.1137.AppImage.sha256"),
        asset("Kovelyx-Linux-amd64-v1.1.1137.deb"),
        asset("Kovelyx-Linux-x86_64-v1.1.1137.flatpak"),
        asset("Kovelyx-Linux-x86_64-v1.1.1137.rpm"),
        asset("Kovelyx-macOS-arm64-v1.1.1137.dmg"),
        asset("Kovelyx-macOS-x86_64-v1.1.1137.dmg"),
        asset("Kovelyx-Windows-v1.1.1137.msi"),
    )

    @Test
    fun `rpm install is offered the rpm asset`() {
        assertEquals(
            "Kovelyx-Linux-x86_64-v1.1.1137.rpm",
            selectFor(LinuxInstallMethod.RPM)?.name,
        )
    }

    @Test
    fun `deb install is offered the deb asset`() {
        assertEquals(
            "Kovelyx-Linux-amd64-v1.1.1137.deb",
            selectFor(LinuxInstallMethod.DEB)?.name,
        )
    }

    @Test
    fun `app image install is offered the image itself and never its zsync file`() {
        assertEquals(
            "Kovelyx-Linux-x86_64-v1.1.1137.AppImage",
            selectFor(LinuxInstallMethod.APP_IMAGE)?.name,
        )
    }

    @Test
    fun `only published x86_64 Linux packages are eligible for updates`() {
        for (arch in listOf("x86_64", "amd64", "x64")) {
            assertTrue(linuxUpdaterArchitectureSupported(arch))
        }
        for (arch in listOf("aarch64", "arm64", "armv7l", "i686", "riscv64", "")) {
            assertFalse(linuxUpdaterArchitectureSupported(arch))
        }
    }

    @Test
    fun `real Linux selector refuses another project and checksum files`() {
        if (System.getProperty("os.name").contains("linux", ignoreCase = true)) {
            assertEquals(listOf("Kovelyx-Linux-"), AppUpdaterPlatform.assetSelector.requiredNamePrefixes)
        }
        val mixed = listOf(
            asset("Other-Linux-x86_64-v1.1.1137.AppImage"),
            asset("Kovelyx-Linux-x86_64-v1.1.1137.AppImage.sha256"),
        ) + releaseAssets
        val chosen = selectBestUpdateAsset(
            mixed, AppUpdateAssetSelector(
                fileExtensions = listOf(".AppImage"),
                preferredNameFragments = listOf("x86_64", "Linux"),
                requiredNamePrefixes = listOf("Kovelyx-Linux-"),
            )
        )
        assertEquals("Kovelyx-Linux-x86_64-v1.1.1137.AppImage", chosen?.name)
    }

    @Test
    fun `app image env wins over every other marker`() {
        val method = resolveLinuxInstallMethod(
            appImagePath = "/home/user/Apps/Nuvio.AppImage",
            appImageExists = true,
            flatpakId = "com.nuvio.media.desktop",
            flatpakInfoExists = true,
            launcherPath = "/opt/Nuvio/bin/Nuvio",
            isOwnedByRpm = { true },
            isOwnedByDpkg = { true },
        )

        assertEquals(LinuxInstallMethod.APP_IMAGE, method)
    }

    @Test
    fun `a stale app image path does not claim the install`() {
        val method = resolveLinuxInstallMethod(
            appImagePath = "/home/user/Apps/deleted.AppImage",
            appImageExists = false,
            flatpakId = null,
            flatpakInfoExists = false,
            launcherPath = "/opt/Nuvio/bin/Nuvio",
            isOwnedByRpm = { true },
            isOwnedByDpkg = { false },
        )

        assertEquals(LinuxInstallMethod.RPM, method)
    }

    @Test
    fun `flatpak is recognised from its sandbox marker file alone`() {
        val method = resolveLinuxInstallMethod(
            appImagePath = null,
            appImageExists = false,
            flatpakId = null,
            flatpakInfoExists = true,
            launcherPath = "/app/opt/Nuvio/bin/Nuvio",
            isOwnedByRpm = { false },
            isOwnedByDpkg = { false },
        )

        assertEquals(LinuxInstallMethod.FLATPAK, method)
    }

    @Test
    fun `dpkg ownership is only consulted when rpm does not claim the launcher`() {
        var rpmQueried = false
        var dpkgQueried = false

        val method = resolveLinuxInstallMethod(
            appImagePath = null,
            appImageExists = false,
            flatpakId = null,
            flatpakInfoExists = false,
            launcherPath = "/opt/Nuvio/bin/Nuvio",
            isOwnedByRpm = { rpmQueried = true; false },
            isOwnedByDpkg = { dpkgQueried = true; true },
        )

        assertEquals(LinuxInstallMethod.DEB, method)
        assertTrue(rpmQueried)
        assertTrue(dpkgQueried)
    }

    @Test
    fun `an unknown launcher is never handed to a package manager`() {
        var queried = false

        val method = resolveLinuxInstallMethod(
            appImagePath = null,
            appImageExists = false,
            flatpakId = null,
            flatpakInfoExists = false,
            launcherPath = null,
            isOwnedByRpm = { queried = true; true },
            isOwnedByDpkg = { queried = true; true },
        )

        assertEquals(LinuxInstallMethod.UNKNOWN, method)
        assertFalse(queried)
    }

    @Test
    fun `the jpackage launcher is preferred over the process command`() {
        assertEquals(
            "/opt/nuvio/bin/Nuvio",
            linuxLauncherPathFrom(
                jpackageAppPath = "/opt/nuvio/bin/Nuvio",
                processCommand = "/usr/lib/jvm/java-17-openjdk/bin/java",
            ),
        )
    }

    // rpm -qf answers for the distribution's own java package, so trusting the
    // JVM path would report a source run as a packaged install.
    @Test
    fun `a jvm process command is not treated as the installed launcher`() {
        assertNull(
            linuxLauncherPathFrom(
                jpackageAppPath = null,
                processCommand = "/usr/lib/jvm/java-17-openjdk/bin/java",
            ),
        )
    }

    @Test
    fun `a real launcher process command is still used when jpackage is silent`() {
        assertEquals(
            "/opt/nuvio/bin/Nuvio",
            linuxLauncherPathFrom(
                jpackageAppPath = "   ",
                processCommand = "/opt/nuvio/bin/Nuvio",
            ),
        )
    }

    @Test
    fun `an unresolved install keeps the previous asset list`() {
        assertEquals(
            listOf(".deb", ".AppImage"),
            linuxUpdateFileExtensions(LinuxInstallMethod.UNKNOWN),
        )
    }

    @Test
    fun `a package install is handed to the desktop package handler`() {
        val command = linuxInstallerCommand(
            method = LinuxInstallMethod.RPM,
            updateFile = File("/tmp/updates/Nuvio.rpm"),
            appImagePath = null,
            currentPid = 4242L,
        )

        assertEquals(listOf("xdg-open", File("/tmp/updates/Nuvio.rpm").absolutePath), command)
    }

    @Test
    fun `an app image replaces itself once this process is gone`() {
        val command = linuxInstallerCommand(
            method = LinuxInstallMethod.APP_IMAGE,
            updateFile = File("/tmp/updates/Nuvio.AppImage"),
            appImagePath = "/home/user/Apps/Nuvio.AppImage",
            currentPid = 4242L,
        )

        assertEquals("sh", command[0])
        assertEquals("-c", command[1])
        val script = command[2]
        val downloadedPath = File("/tmp/updates/Nuvio.AppImage").absolutePath
        assertTrue(script.contains("kill -0 4242"))
        assertTrue(script.contains("cp -f -- '$downloadedPath' '/home/user/Apps/Nuvio.AppImage.kovelyx-staged-4242'"))
        assertTrue(script.contains("mv -f -- '/home/user/Apps/Nuvio.AppImage.kovelyx-staged-4242' '/home/user/Apps/Nuvio.AppImage'"))
        assertTrue(script.contains("exec '/home/user/Apps/Nuvio.AppImage'"))
        assertTrue(script.contains("xdg-open '$downloadedPath'"))
    }

    @Test
    fun `app image replacement aborts before rename if old process is still alive`() {
        val script = appImageReplaceScript(
            downloadedPath = "/tmp/updates/Kovelyx-new.AppImage",
            appImagePath = "/home/user/Kovelyx.AppImage",
            currentPid = 4242L,
        )
        val guard = script.indexOf("if kill -0 4242 2>/dev/null; then")
        val rename = script.indexOf("if mv -f --")
        assertTrue(guard >= 0 && rename > guard, "Old process must stop before any rename")
        assertTrue(script.contains("rm -f -- '/home/user/Kovelyx.AppImage.kovelyx-staged-4242'"))
    }

    @Test
    fun `an app image with no known path falls back to the desktop handler`() {
        val command = linuxInstallerCommand(
            method = LinuxInstallMethod.APP_IMAGE,
            updateFile = File("/tmp/updates/Nuvio.AppImage"),
            appImagePath = null,
            currentPid = 4242L,
        )

        assertEquals(listOf("xdg-open", File("/tmp/updates/Nuvio.AppImage").absolutePath), command)
    }

    @Test
    fun `a quote in the app image path cannot break out of the script`() {
        val script = appImageReplaceScript(
            downloadedPath = "/tmp/updates/Nuvio.AppImage",
            appImagePath = "/home/user/Ar'jun/Nuvio.AppImage",
            currentPid = 7L,
        )

        assertTrue(script.contains("'/home/user/Ar'\\''jun/Nuvio.AppImage'"))
        assertFalse(script.contains("Ar'jun"))
    }

    private fun selectFor(method: LinuxInstallMethod): AppUpdateAssetCandidate? =
        selectBestUpdateAsset(
            assets = releaseAssets,
            selector = AppUpdateAssetSelector(
                fileExtensions = linuxUpdateFileExtensions(method),
                preferredNameFragments = listOf("x64", "x86_64", "amd64", "linux"),
                fallbackNameFragments = listOf("universal", "all"),
                requiredNamePrefixes = listOf("Kovelyx-Linux-"),
            ),
        )

    private fun asset(name: String): AppUpdateAssetCandidate =
        AppUpdateAssetCandidate(
            name = name,
            downloadUrl = "https://example.test/$name",
        )
}
