package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableBackupMaintenanceTest {
    @Test fun manyBackupsReclaimPayloadAndPreserveAllUserData() {
        val home = Files.createTempDirectory("kovelyx-portable").toFile()
        try {
            fun put(base: File, path: String, text: String) = File(base, path).apply { parentFile.mkdirs(); writeText(text) }
            val install = File(home, "Kovelyx").apply { mkdirs() }
            put(install, "Kovelyx.exe", "current")
            put(install, "Kovelyx.portable", "portable")
            val backups = (1..10).map { n ->
                val dir = File(home, ".kovelyx-previous-" + n.toString(16).padStart(32, '0'))
                put(dir, "Kovelyx.portable", "portable")
                put(dir, ".kovelyx-update-owner", install.canonicalPath)
                put(dir, "Kovelyx.exe", "binary")
                put(dir, "app/Kovelyx.cfg", "app.mainclass=com.kovelyx.app.MainKt")
                put(dir, "app/Kovelyx.jar", "binary")
                put(dir, "runtime/lib/modules", "binary")
                put(dir, "runtime/bin/java.dll", "binary")
                put(dir, "library.json", "user library")
                put(dir, "app/profiles.json", "user profiles")
                put(dir, "runtime/video.mkv", "user media")
                dir
            }
            assertEquals(240L, PortableBackupMaintenance.clean(install))
            assertEquals(0L, PortableBackupMaintenance.clean(install))
            backups.forEach { dir ->
                assertEquals("user library", File(dir,"library.json").readText())
                assertEquals("user profiles", File(dir,"app/profiles.json").readText())
                assertEquals("user media", File(dir,"runtime/video.mkv").readText())
            }
            assertEquals("current", File(install,"Kovelyx.exe").readText())
        } finally { home.deleteRecursively() }
    }

    @Test fun unconfirmedLegacyAndOtherInstallationsKeepRollback() {
        val home = Files.createTempDirectory("kovelyx-rollback").toFile()
        try {
            val install = File(home,"Kovelyx").apply { mkdirs() }
            File(install,"Kovelyx.portable").writeText("")
            File(install,"Kovelyx.exe").writeText("current")
            val dir = File(home,".kovelyx-previous-" + "a".repeat(32)).apply { mkdirs() }
            File(dir,"app").mkdirs()
            File(dir,"app/Kovelyx.cfg").writeText("app.mainclass=com.kovelyx.app.MainKt")
            File(dir,"Kovelyx.portable").writeText("")
            val binary = File(dir,"Kovelyx.exe").apply { writeText("rollback") }
            assertEquals(0L, PortableBackupMaintenance.clean(install))
            assertTrue(binary.exists())
            File(dir,".kovelyx-update-owner").writeText(File(home,"OtherKovelyx").canonicalPath)
            assertEquals(0L, PortableBackupMaintenance.clean(install, System.currentTimeMillis()+600_000))
            assertTrue(binary.exists())
            File(dir,".kovelyx-update-owner").delete()
            assertEquals(8L, PortableBackupMaintenance.clean(install, System.currentTimeMillis()+600_000))
        } finally { home.deleteRecursively() }
    }
}
