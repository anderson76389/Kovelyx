package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifiedAndroidPlatformBundleTest {
    private val tag = "v1.1.1168"
    private val phone = "Kovelyx-Android-$tag.apk"
    private val tv = "Kovelyx-TV-$tag-universal.apk"
    private val mobileBytes = ByteArray(1_000_012) { 21 }
    private val tvBytes = ByteArray(1_000_015) { 42 }

    private fun sha(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun writeBundle(dir: File, badDigest: Boolean = false, unexpected: Boolean = false): File {
        val bundle = File(dir, "Kovelyx-Android-$tag.zip")
        ZipOutputStream(bundle.outputStream()).use { zip ->
            for ((name, data) in listOf(phone to mobileBytes, tv to tvBytes)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("SHA256SUMS.txt"))
            val manifest = (if (badDigest) "0".repeat(64) else sha(mobileBytes)) +
                "  $phone\n" + sha(tvBytes) + "  $tv\n"
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            if (unexpected) {
                zip.putNextEntry(ZipEntry("untrusted.apk"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }
        return bundle
    }

    @Test fun extractsOnlyPhoneApkAndKeepsUserData() {
        val home = Files.createTempDirectory("kv-phone-pack").toFile()
        try {
            val library = File(home, "library.json").apply { writeText("keep me") }
            val updates = File(home, "updates").apply { mkdirs() }
            val extracted = VerifiedAndroidPlatformBundle.extractMobileApk(writeBundle(updates), updates)
            assertContentEquals(mobileBytes, extracted.readBytes())
            assertFalse(File(updates, tv).exists())
            assertTrue(library.readText() == "keep me")
            assertTrue(VerifiedAndroidPlatformBundle.isBundleName("Kovelyx-Android-$tag.zip"))
            assertFalse(VerifiedAndroidPlatformBundle.isBundleName("Kovelyx-TV-$tag-universal.apk"))
        } finally { home.deleteRecursively() }
    }

    @Test fun rejectsTamperingAndUnexpectedFiles() {
        val home = Files.createTempDirectory("kv-phone-corrupt").toFile()
        try {
            val updates = File(home, "updates").apply { mkdirs() }
            assertFailsWith<IllegalStateException> {
                VerifiedAndroidPlatformBundle.extractMobileApk(writeBundle(updates, badDigest = true), updates)
            }
            assertFalse(File(updates, phone).exists())
            assertFailsWith<IllegalStateException> {
                VerifiedAndroidPlatformBundle.extractMobileApk(writeBundle(updates, unexpected = true), updates)
            }
        } finally { home.deleteRecursively() }
    }
}
