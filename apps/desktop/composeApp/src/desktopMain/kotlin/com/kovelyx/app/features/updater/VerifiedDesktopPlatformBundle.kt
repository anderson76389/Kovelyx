package com.kovelyx.app.features.updater

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Strictly extracts the one installer matching the already-installed desktop format. */
internal object VerifiedDesktopPlatformBundle {
    private val namePattern = Regex(
        """^Kovelyx-(Windows|Linux)-(v[0-9]+\.[0-9]+\.[0-9]+(?:-(?:alpha|beta|rc))?)\.zip$"""
    )
    private val manifestLine = Regex("""^([a-fA-F0-9]{64})  ([A-Za-z0-9._-]+)$""")

    fun isBundleName(name: String): Boolean = namePattern.matches(name)

    fun extract(
        archive: File,
        directory: File,
        isWindows: Boolean,
        isWindowsPortable: Boolean,
        linuxFormat: String,
    ): File {
        val match = namePattern.matchEntire(archive.name)
            ?: error("Archive de plateforme Kovelyx non reconnue.")
        val platform = match.groupValues[1]
        val tag = match.groupValues[2]
        check((platform == "Windows") == isWindows) {
            "Une archive destinée à un autre système a été sélectionnée."
        }
        val expected = if (isWindows) setOf(
            "Kovelyx-Windows-$tag.msi",
            "Kovelyx-Windows-Setup-$tag.exe",
            "Kovelyx-Windows-Portable-$tag.zip",
        ) else setOf(
            "Kovelyx-Linux-x86_64-$tag.AppImage",
            "Kovelyx-Linux-amd64-$tag.deb",
            "Kovelyx-Linux-x86_64-$tag.rpm",
        )
        val wanted = if (isWindows) {
            if (isWindowsPortable) "Kovelyx-Windows-Portable-$tag.zip"
            else "Kovelyx-Windows-$tag.msi"
        } else when (linuxFormat) {
            "APP_IMAGE" -> "Kovelyx-Linux-x86_64-$tag.AppImage"
            "RPM" -> "Kovelyx-Linux-x86_64-$tag.rpm"
            "DEB", "UNKNOWN" -> "Kovelyx-Linux-amd64-$tag.deb"
            else -> error("Installation Linux non compatible avec une archive autonome.")
        }
        directory.mkdirs()
        ZipFile(archive).use { zipped ->
            val names = zipped.entries().asSequence().map { it.name }.toList()
            check(names.size == 4 && names.toSet() == expected + "SHA256SUMS.txt") {
                "Contenu du pack Kovelyx incomplet ou inattendu."
            }
            val manifest = zipped.getInputStream(zipped.getEntry("SHA256SUMS.txt"))
                .bufferedReader().use { it.readText() }
            check(manifest.length <= 2048) { "Manifeste de vérification trop volumineux." }
            val digests = manifest.lineSequence().filter { it.isNotBlank() }.map { line ->
                val found = manifestLine.matchEntire(line)
                    ?: error("Le pack possède un manifeste SHA-256 invalide.")
                found.groupValues[2] to found.groupValues[1]
            }.toList()
            check(digests.size == expected.size && digests.map { it.first }.toSet() == expected) {
                "Un installateur manque dans le manifeste de plateforme."
            }
            val entry = zipped.getEntry(wanted) ?: error("Installateur compatible absent.")
            check(entry.size in 1_000_000L..800_000_000L) {
                "Taille de l'installateur non valide."
            }
            val destination = File(directory, wanted)
            val temp = File(directory, "$wanted.part")
            temp.delete()
            try {
                val sha256 = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                zipped.getInputStream(entry).use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            bytes += count
                            check(bytes <= entry.size) { "Installateur interne tronqué." }
                            sha256.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(bytes == entry.size) { "Installateur interne tronqué." }
                val actual = sha256.digest().joinToString("") { "%02x".format(it) }
                check(actual.equals(digests.single { it.first == wanted }.second, ignoreCase = true)) {
                    "Empreinte de l'installateur différente du manifeste."
                }
                if (destination.exists()) destination.delete()
                if (!temp.renameTo(destination)) {
                    temp.copyTo(destination, overwrite = true)
                    temp.delete()
                }
                return destination
            } finally {
                temp.delete()
            }
        }
    }
}
