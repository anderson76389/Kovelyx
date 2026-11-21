package com.kovelyx.app.core.storage

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopStorageTest {
    @Test
    fun batch_window_geometry_persists_together_and_avoids_rewrites() {
        val directory = Files.createTempDirectory("desktop-geometry-test")
        val file = directory.resolve("window.properties")
        try {
            val store = DesktopStorage.Store(file)
            val original = mapOf(
                "window_x" to 12f,
                "window_y" to 24f,
                "window_width" to 1280f,
                "window_height" to 800f,
            )
            store.putFloats(original)
            val sentinel = FileTime.fromMillis(1_000L)
            Files.setLastModifiedTime(file, sentinel)
            store.putFloats(original)
            assertEquals(sentinel, Files.getLastModifiedTime(file))

            store.putFloats(original + ("window_width" to 1440f))
            assertNotEquals(sentinel, Files.getLastModifiedTime(file))
            val loaded = DesktopStorage.Store(file)
            assertEquals(12f, loaded.getFloat("window_x"))
            assertEquals(24f, loaded.getFloat("window_y"))
            assertEquals(1440f, loaded.getFloat("window_width"))
            assertEquals(800f, loaded.getFloat("window_height"))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun unchanged_operations_do_not_rewrite_the_store() {
        val directory = Files.createTempDirectory("desktop-storage-test")
        val file = directory.resolve("preferences.properties")
        try {
            val store = DesktopStorage.Store(file)
            store.putString("key", "value")
            val sentinel = FileTime.fromMillis(1_000L)
            Files.setLastModifiedTime(file, sentinel)

            store.putString("key", "value")
            store.remove("missing")
            store.removeAll(listOf("also-missing"))

            assertEquals(sentinel, Files.getLastModifiedTime(file))

            store.putString("key", "updated")

            assertNotEquals(sentinel, Files.getLastModifiedTime(file))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
