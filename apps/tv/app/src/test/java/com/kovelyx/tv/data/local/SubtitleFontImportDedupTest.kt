package com.kovelyx.tv.data.local

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFontImportDedupTest {
    @Test
    fun identicalImportsReuseExistingFontAcrossExtensions() {
        val directory = Files.createTempDirectory("kovelyx-font-dedup").toFile()
        try {
            val existing = File(directory, "123e4567-e89b-12d3-a456-426614174000.ttf")
            val imported = File(directory, "123e4567-e89b-12d3-a456-426614174001.otf")
            val bytes = "OTTO-safe-font-test-bytes".toByteArray()
            existing.writeBytes(bytes)
            imported.writeBytes(bytes)
            assertEquals(existing, SubtitleFontStore.findIdenticalImport(directory, imported))
            // Importing an identical font may remove only the new temporary copy.
            assertEquals(bytes.toList(), existing.readBytes().toList())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun differentFontsAndUntrustedNamesAreNeverReused() {
        val directory = Files.createTempDirectory("kovelyx-font-dedup").toFile()
        try {
            val imported = File(directory, "123e4567-e89b-12d3-a456-426614174001.ttf")
            imported.writeText("same size")
            File(directory, "123e4567-e89b-12d3-a456-426614174002.ttf").writeText("different")
            assertNull(SubtitleFontStore.findIdenticalImport(directory, imported))

            // Identical bytes under an unrelated filename must not become a stored font ID.
            File(directory, "not-an-import.ttf").writeText("same size")
            assertNull(SubtitleFontStore.findIdenticalImport(directory, imported))
        } finally {
            directory.deleteRecursively()
        }
    }
}
