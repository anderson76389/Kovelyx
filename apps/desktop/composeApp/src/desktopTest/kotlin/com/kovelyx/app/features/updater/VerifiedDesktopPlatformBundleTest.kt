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

class VerifiedDesktopPlatformBundleTest {
    private val tag = "v1.1.1168"
    private val windows = listOf(
        "Kovelyx-Windows-$tag.msi",
        "Kovelyx-Windows-Setup-$tag.exe",
        "Kovelyx-Windows-Portable-$tag.zip",
    )
    private val linux = listOf(
        "Kovelyx-Linux-x86_64-$tag.AppImage",
        "Kovelyx-Linux-amd64-$tag.deb",
        "Kovelyx-Linux-x86_64-$tag.rpm",
    )

    private fun sha(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun createBundle(
        root: File, platform: String, files: List<String>, tamper: Boolean = false,
    ): File {
        val archive = File(root, "Kovelyx-$platform-$tag.zip")
        val bodies = files.mapIndexed { index, name ->
            name to ByteArray(1_000_009) { index.toByte() }
        }.toMap()
        ZipOutputStream(archive.outputStream()).use { zip ->
            for ((name, body) in bodies) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("SHA256SUMS.txt"))
            val manifest = files.sorted().joinToString("\n", postfix = "\n") {
                (if (tamper && it == files.first()) "0".repeat(64) else sha(bodies.getValue(it))) +
                    "  $it"
            }
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        return archive
    }

    @Test fun installedWindowsReceivesMsiAndPortableWindowsReceivesNestedZip() {
        val home = Files.createTempDirectory("kv-windows-pack").toFile()
        try {
            val archive = createBundle(home, "Windows", windows)
            val installed = File(home, "installed")
            val portable = File(home, "portable")
            val msi = VerifiedDesktopPlatformBundle.extract(archive, installed, true, false, "UNKNOWN")
            val zip = VerifiedDesktopPlatformBundle.extract(archive, portable, true, true, "UNKNOWN")
            assertTrue(msi.name.endsWith(".msi"))
            assertTrue(zip.name.endsWith(".zip"))
            assertFalse(File(installed, windows[2]).exists())
            assertContentEquals(ByteArray(1_000_009) { windows.indexOf(msi.name).toByte() }, msi.readBytes())
        } finally { home.deleteRecursively() }
    }

    @Test fun linuxKeepsAppImageDebAndRpmInstallTypes() {
        val home = Files.createTempDirectory("kv-linux-pack").toFile()
        try {
            val archive = createBundle(home, "Linux", linux)
            for ((method, ext) in listOf("APP_IMAGE" to ".AppImage", "DEB" to ".deb", "RPM" to ".rpm")) {
                val output = File(home, method)
                val selected = VerifiedDesktopPlatformBundle.extract(archive, output, false, false, method)
                assertTrue(selected.name.endsWith(ext), "wrong format for $method")
                assertTrue(output.listFiles().orEmpty().size == 1)
            }
        } finally { home.deleteRecursively() }
    }

    @Test fun tamperedManifestAndWrongPlatformAreRejected() {
        val home = Files.createTempDirectory("kv-invalid-pack").toFile()
        try {
            val archive = createBundle(home, "Linux", linux, tamper = true)
            assertFailsWith<IllegalStateException> {
                VerifiedDesktopPlatformBundle.extract(archive, File(home, "output"), false, false, "APP_IMAGE")
            }
            assertFailsWith<IllegalStateException> {
                VerifiedDesktopPlatformBundle.extract(archive, File(home, "output"), true, false, "UNKNOWN")
            }
        } finally { home.deleteRecursively() }
    }
}
