package com.kovelyx.cloud.media

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.profiles.ProfileLocalImages
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class DesktopProfileMediaFiles : ProfileMediaFiles {
    private val settings = DesktopStorage.store("kovelyx_cloud_profile_media")
    private val folder get() = DesktopStorage.rootDir.resolve("cloud-profile-media")
    private val json = Json { ignoreUnknownKeys = true }
    override fun loadState(key: String): ProfileMediaState? = settings.getString(sha256(key.toByteArray()))?.let {
        json.decodeFromString<ProfileMediaState>(it)
    }
    override fun saveState(key: String, state: ProfileMediaState) {
        settings.putString(sha256(key.toByteArray()), json.encodeToString(state))
    }
    private fun privateFile(uri: String): Path {
        require(uri.startsWith("file:"))
        val file = Path.of(URI(uri)).toRealPath()
        require(file.startsWith(DesktopStorage.rootDir.toRealPath())) { "Image hors du dossier privé Kovelyx." }
        return file
    }
    override fun exists(uri: String) = runCatching { Files.isRegularFile(privateFile(uri)) }.getOrDefault(false)
    override fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    override suspend fun encodeLocal(uri: String, background: Boolean): ProfileImageBytes = withContext(Dispatchers.IO) {
        val file = privateFile(uri)
        require(Files.size(file) in 1..18_000_000L)
        val header = Files.newInputStream(file).use { it.readNBytes(12) }
        if (header.size >= 6 && String(header, 0, 6, Charsets.US_ASCII).startsWith("GIF8")) {
            require(Files.size(file) <= MAX_PROFILE_MEDIA_BYTES) { "Le GIF animé dépasse 2 Mio ; choisissez une animation plus légère pour la synchroniser." }
            return@withContext ProfileImageBytes("image/gif", Files.readAllBytes(file))
        }
        val bitmap = ProfileLocalImages.readAndResize(file, background)
        try {
            val output = ByteArrayOutputStream()
            check(ImageIO.write(bitmap, if (background) "jpg" else "png", output))
            val bytes = output.toByteArray()
            require(bytes.size <= MAX_PROFILE_MEDIA_BYTES) { "Image trop volumineuse après optimisation." }
            ProfileImageBytes(if (background) "image/jpeg" else "image/png", bytes)
        } finally { bitmap.flush() }
    }
    override suspend fun saveDownloaded(key: String, version: String, image: ProfileImageBytes): String = withContext(Dispatchers.IO) {
        require(version.matches(Regex("[a-f0-9]{64}")))
        Files.createDirectories(folder)
        val prefix = sha256(key.toByteArray()) + "-"
        val extension = when(image.mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> error("Type d’image distant invalide") }
        val file = folder.resolve(prefix + version + "." + extension)
        val pending = Files.createTempFile(folder, "download-", ".tmp")
        try { Files.write(pending, image.bytes); Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(pending) }
        Files.list(folder).use { files -> files.filter { it.fileName.toString().startsWith(prefix) && it != file }.forEach { Files.deleteIfExists(it) } }
        file.toUri().toString()
    }
}
