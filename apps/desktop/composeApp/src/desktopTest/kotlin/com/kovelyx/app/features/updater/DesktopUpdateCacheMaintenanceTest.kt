package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateCacheMaintenanceTest {
    @Test fun twentySequentialWindowsAndLinuxInstallerUpdatesNeverAccumulateInManagedCache() {
        val home = Files.createTempDirectory("kovelyx-updater-repeat").toFile()
        try {
            val updates = File(home, "updates").apply { mkdirs() }
            val profile = File(home, "profile.json").apply { writeText("keep the user profile") }
            repeat(20) { n ->
                val version = "1.1.${1200 + n}"
                val names = listOf(
                    "Kovelyx-Windows-v$version.msi",
                    "Kovelyx-Windows-Setup-v$version.exe",
                    "Kovelyx-Linux-x86_64-v$version.AppImage",
                    "Kovelyx-Linux-amd64-v$version.deb",
                    "Kovelyx-Linux-x86_64-v$version.rpm",
                )
                names.forEach { File(updates, it).writeBytes(ByteArray(1024)) }
                assertEquals(5L * 1024, DesktopUpdateCacheMaintenance.clean(updates, version))
                assertTrue(updates.listFiles().orEmpty().isEmpty(), "Updater files left after $version")
                assertEquals("keep the user profile", profile.readText())
            }
        } finally { home.deleteRecursively() }
    }

    @Test fun publishedNamesCurrentVersionsAndLegacyMsiAreRecognized() {
        val home = Files.createTempDirectory("kovelyx-desktop-updates").toFile()
        try {
            val dir = File(home, "updates").apply { mkdirs() }
            val installed = listOf(
                "Kovelyx-Windows-v1.1.1117.msi",
                "Kovelyx-Windows-Setup-v1.1.1117.exe",
                "Kovelyx-Windows-Portable-v1.1.1116.zip",
                "Kovelyx-Windows-x64-1.1.1117.msi",
                "Kovelyx-Windows-v1.1.1117-beta.msi",
            ).map { File(dir,it).apply { writeText("old") } }
            val newer = File(dir,"Kovelyx-Windows-v1.1.1118.msi").apply { writeText("new") }
            val future = File(dir,"Kovelyx-Windows-v1.1.1118-beta.msi").apply { writeText("new") }
            assertEquals(15L, DesktopUpdateCacheMaintenance.clean(dir,"1.1.1117"))
            installed.forEach { assertFalse(it.exists(), it.path) }
            assertTrue(newer.exists()); assertTrue(future.exists())
        } finally { home.deleteRecursively() }
    }
}
