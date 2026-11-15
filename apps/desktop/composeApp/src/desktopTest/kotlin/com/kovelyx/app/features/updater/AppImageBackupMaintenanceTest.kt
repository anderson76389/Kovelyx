package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppImageBackupMaintenanceTest {
    private val elf = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 1, 2, 3, 4)

    @Test fun markedBackupIsReclaimedAfterNewAppStartsWithoutDeletingOtherFiles() {
        val home = Files.createTempDirectory("kovelyx-appimage-clean").toFile()
        try {
            val app = File(home, "Kovelyx.AppImage").apply { writeBytes(elf) }
            val backup = File(home, "Kovelyx.AppImage.kovelyx-previous").apply { writeBytes(elf) }
            val marker = File(home, "Kovelyx.AppImage.kovelyx-previous.owner")
                .apply { writeText(AppImageBackupMaintenance.OWNER_MARKER_CONTENT) }
            val personal = File(home, "downloads").apply { mkdirs() }
            val photo = File(personal, "user-photo.jpg").apply { writeText("keep") }

            assertEquals(elf.size.toLong(), AppImageBackupMaintenance.clean(app))
            assertFalse(backup.exists())
            assertFalse(marker.exists())
            assertTrue(app.exists())
            assertEquals("keep", photo.readText())
            assertEquals(0L, AppImageBackupMaintenance.clean(app))
        } finally {
            home.deleteRecursively()
        }
    }

    @Test fun unmarkedInvalidAndUnrelatedBackupsAreNeverRemoved() {
        val home = Files.createTempDirectory("kovelyx-appimage-guard").toFile()
        try {
            val app = File(home, "Kovelyx.AppImage").apply { writeBytes(elf) }
            val backup = File(home, "Kovelyx.AppImage.kovelyx-previous").apply { writeBytes(elf) }
            val marker = File(home, "Kovelyx.AppImage.kovelyx-previous.owner")
            assertEquals(0L, AppImageBackupMaintenance.clean(app))
            marker.writeText("somebody else's file")
            assertEquals(0L, AppImageBackupMaintenance.clean(app))
            marker.writeText(AppImageBackupMaintenance.OWNER_MARKER_CONTENT)
            backup.writeText("not an executable, preserve me")
            assertEquals(0L, AppImageBackupMaintenance.clean(app))
            assertTrue(backup.exists())
            backup.writeBytes(elf)
            assertEquals(0L, AppImageBackupMaintenance.clean(File(home, "Other.AppImage")))
            assertTrue(backup.exists())
        } finally {
            home.deleteRecursively()
        }
    }

    @Test fun linkedBackupDoesNotDeleteSomeoneElsesImage() {
        val home = Files.createTempDirectory("kovelyx-appimage-symlink").toFile()
        try {
            val app = File(home, "Kovelyx.AppImage").apply { writeBytes(elf) }
            val personal = File(home, "personal.AppImage").apply { writeBytes(elf) }
            val backup = File(home, "Kovelyx.AppImage.kovelyx-previous")
            val marker = File(home, "Kovelyx.AppImage.kovelyx-previous.owner")
                .apply { writeText(AppImageBackupMaintenance.OWNER_MARKER_CONTENT) }
            if (runCatching { Files.createSymbolicLink(backup.toPath(), personal.toPath()) }.isSuccess) {
                assertEquals(0L, AppImageBackupMaintenance.clean(app))
                assertTrue(personal.exists())
                assertTrue(backup.exists())
                assertTrue(marker.exists())
            }
        } finally {
            home.deleteRecursively()
        }
    }
}
