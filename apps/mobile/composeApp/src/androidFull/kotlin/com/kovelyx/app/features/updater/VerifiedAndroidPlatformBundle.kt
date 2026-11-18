package com.kovelyx.app.features.updater

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Extract only the signed mobile APK from the official four-platform Release pack. */
internal object VerifiedAndroidPlatformBundle {
    private val bundlePattern =
        Regex("""^Kovelyx-Android-(v[0-9]+\.[0-9]+\.[0-9]+(?:-(?:alpha|beta|rc))?)\.zip$""")
    private val manifestLine = Regex("""^([a-fA-F0-9]{64})  ([A-Za-z0-9._-]+)$""")

    fun isBundleName(name: String): Boolean = bundlePattern.matches(name)

    fun extractMobileApk(bundle: File, directory: File): File {
        val tag = bundlePattern.matchEntire(bundle.name)?.groupValues?.get(1)
            ?: error("Archive Android Kovelyx non reconnue.")
        val mobileName = "Kovelyx-Android-$tag.apk"
        val expected = setOf(mobileName)
        directory.mkdirs()
        ZipFile(bundle).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            check(names.size == 2 && names.toSet() == expected + "SHA256SUMS.txt") {
                "Archive Android incorrecte : fichiers internes inattendus."
            }
            val manifest = zip.getInputStream(zip.getEntry("SHA256SUMS.txt")).bufferedReader()
                .use { reader -> reader.readText() }
            check(manifest.length <= 1024) { "Manifeste de vérification trop volumineux." }
            val checksums = manifest.lineSequence().filter { it.isNotBlank() }.map { line ->
                val match = manifestLine.matchEntire(line)
                    ?: error("Manifeste SHA-256 de l'archive Android invalide.")
                match.groupValues[2] to match.groupValues[1]
            }.toList()
            check(checksums.size == 1 && checksums.map { it.first }.toSet() == expected) {
                "Le manifeste ne correspond pas à l'APK mobile."
            }
            val inner = zip.getEntry(mobileName) ?: error("APK téléphone absent.")
            check(inner.size in 1_000_000L..300_000_000L) { "Taille APK interne invalide." }
            val destination = File(directory, mobileName)
            val temporary = File(directory, "$mobileName.part")
            temporary.delete()
            try {
                val hash = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                zip.getInputStream(inner).use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            bytes += count
                            check(bytes <= inner.size) { "APK incomplet ou malformé." }
                            hash.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                check(bytes == inner.size) { "APK interne tronqué." }
                val actual = hash.digest().joinToString("") { "%02x".format(it) }
                check(actual.equals(checksums.single { it.first == mobileName }.second, ignoreCase = true)) {
                    "Empreinte du téléphone différente du manifeste du pack."
                }
                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                return destination
            } finally {
                temporary.delete()
            }
        }
    }
}
