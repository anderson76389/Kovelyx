package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCacheMaintenanceTest {
    @Test fun installedPackagesAndAbandonedDownloadsAreReclaimedWithoutTouchingLibrary() {
        val home = Files.createTempDirectory("kovelyx-storage-test").toFile()
        try {
            val updates = File(home, "updates").apply { mkdirs() }
            fun put(path: String) = File(home, path).apply { parentFile.mkdirs(); writeBytes(ByteArray(1024)) }
            val library = put("library/library.json")
            val profile = put("profiles/account.json")
            val media = put("cache/movie.mkv")
            val notes = put("updates/notes.txt")
            val newer = put("updates/Kovelyx-next.apk")
            val current = put("updates/Kovelyx-current.apk")
            val old = put("updates/Nuvio-old.apk")
            val part = put("updates/Kovelyx-current.apk.part").apply { setLastModified(1L) }
            val freshPart = put("updates/Kovelyx-next.apk.part")
            val unknown = put("updates/Kovelyx-unreadable.apk")
            val expired = put("updates/Nuvio-expired.apk").apply { setLastModified(1L) }
            val deleted = UpdateCacheMaintenance.clean(updates, { f -> when(f.name) {
                newer.name -> false
                current.name, old.name -> true
                else -> null
            } })
            assertEquals(4096L, deleted)
            listOf(current, old, part, expired).forEach { assertFalse(it.exists(), it.path) }
            listOf(library, profile, media, notes, newer, unknown, freshPart).forEach { assertEquals(1024L, it.length(), it.path) }
            assertEquals(0L, UpdateCacheMaintenance.clean(updates, { false }))
        } finally { home.deleteRecursively() }
    }

    @Test fun repeatedInstalledVersionsDoNotAccumulate() {
        val home = Files.createTempDirectory("kovelyx-storage-repeat").toFile()
        try {
            val updates = File(home, "updates").apply { mkdirs() }
            repeat(20) { version ->
                File(updates, "Kovelyx-v1.1.$version.apk").writeBytes(ByteArray(8192))
                assertEquals(8192L, UpdateCacheMaintenance.clean(updates, { true }))
                assertTrue(updates.listFiles().orEmpty().isEmpty())
            }
        } finally { home.deleteRecursively() }
    }

    @Test fun directoriesOtherProductsAndSymbolicLinksArePreserved() {
        val home = Files.createTempDirectory("kovelyx-storage-links").toFile()
        try {
            val updates = File(home, "updates").apply { mkdirs() }
            val outside = File(home, "library.json").apply { writeText("saved library") }
            val other = File(updates, "other.apk").apply { writeText("other") }
            val folder = File(updates, "Kovelyx-folder.apk").apply { mkdirs() }
            val link = File(updates, "Kovelyx-link.apk")
            val linked = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
            assertEquals(0L, UpdateCacheMaintenance.clean(home, { true }))
            assertEquals(0L, UpdateCacheMaintenance.clean(updates, { true }))
            assertEquals("saved library", outside.readText())
            assertTrue(other.exists()); assertTrue(folder.isDirectory)
            if (linked) assertTrue(Files.isSymbolicLink(link.toPath()))
        } finally { home.deleteRecursively() }
    }
}
