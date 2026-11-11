package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateCacheMaintenanceTest {
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
