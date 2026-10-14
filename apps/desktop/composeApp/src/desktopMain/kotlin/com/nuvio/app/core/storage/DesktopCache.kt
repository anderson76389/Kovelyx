package com.nuvio.app.core.storage

import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal object DesktopCache {
    fun installVersionedFiles(namespace: String, files: Map<String, ByteArray>): Path {
        require(files.isNotEmpty())
        val directory = versionedDirectory(namespace, files)
        files.forEach { (relativePath, bytes) ->
            writeIfChanged(resolveTarget(directory, relativePath), bytes)
        }
        return directory
    }

    fun installVersionedResources(namespace: String, resources: Map<String, URL>): Path {
        require(resources.isNotEmpty())
        val directory = versionedDirectory(
            namespace,
            resources.mapValues { (_, url) -> resourceStamp(url).toByteArray(Charsets.UTF_8) },
        )
        resources.forEach { (relativePath, url) ->
            val target = resolveTarget(directory, relativePath)
            if (!Files.exists(target)) {
                // URL.openStream() defaults to caching JarURLConnection's JarFile
                // indefinitely on Windows, retaining a lock even after close().
                // Disabling connection caching allows replacing/deleting old engine JARs.
                val connection = url.openConnection().apply { useCaches = false }
                connection.getInputStream().use { input ->
                    writeAtomically(target) { pending -> Files.copy(input, pending, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
        return directory
    }

    private fun versionedDirectory(namespace: String, versionInputs: Map<String, ByteArray>): Path {
        require(namespace.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256")
        versionInputs.toSortedMap().forEach { (path, bytes) ->
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(bytes)
        }
        val version = digest.digest().joinToString("") { byte -> "%02x".format(byte) }.take(24)
        val directory = DesktopStorage.cacheDir.resolve(namespace).resolve(version).normalize()
        require(directory.startsWith(DesktopStorage.cacheDir))
        return directory
    }

    private fun resolveTarget(directory: Path, relativePath: String): Path {
        val target = directory.resolve(relativePath).normalize()
        require(target.startsWith(directory))
        return target
    }

    private fun resourceStamp(url: URL): String = when (url.protocol.lowercase()) {
        // FileURLConnection.contentLengthLong may leave a handle open on Windows.
        "file" -> {
            val path = Path.of(url.toURI())
            "${Files.size(path)}:${Files.getLastModifiedTime(path).toMillis()}"
        }
        // JarURLConnection caches JarFile handles by default, preventing JAR cleanup
        // and replacement on Windows even after the input stream has been closed.
        "jar" -> {
            val connection = url.openConnection() as JarURLConnection
            connection.useCaches = false
            connection.jarFile.use { jar ->
                val entry = requireNotNull(jar.getJarEntry(connection.entryName)) {
                    "Missing JAR resource: $url"
                }
                "${entry.size}:${entry.crc}"
            }
        }
        else -> {
            val connection = url.openConnection()
            connection.useCaches = false
            "${connection.contentLengthLong}:${connection.lastModified}"
        }
    }

    private fun writeIfChanged(target: Path, bytes: ByteArray) {
        if (Files.exists(target) && Files.readAllBytes(target).contentEquals(bytes)) return
        writeAtomically(target) { pending -> Files.write(pending, bytes) }
    }

    private fun writeAtomically(target: Path, write: (Path) -> Unit) {
        Files.createDirectories(target.parent)
        val pending = Files.createTempFile(target.parent, target.fileName.toString(), ".part")
        try {
            write(pending)
            runCatching {
                Files.move(
                    pending,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }
}
