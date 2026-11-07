package com.kovelyx.cloud.media

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.features.profiles.ProfileLocalImages
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal class DesktopProfileMediaFiles : ProfileMediaFiles {
    private val settings = DesktopStorage.store("kovelyx_cloud_profile_media")
    private val folder get() = DesktopStorage.rootDir.resolve("cloud-profile-media")
    private val json = Json { ignoreUnknownKeys = true }
    private val stateLock = Any()
    override fun loadState(key: String): ProfileMediaState? = synchronized(stateLock) {
        settings.getString(sha256(key.toByteArray()))?.let { json.decodeFromString<ProfileMediaState>(it) }
    }
    override fun saveState(key: String, state: ProfileMediaState) = synchronized(stateLock) {
        settings.putString(sha256(key.toByteArray()), json.encodeToString(state))
    }
    override fun compareAndSaveState(key: String, expected: ProfileMediaState?, next: ProfileMediaState): Boolean = synchronized(stateLock) {
        if (loadState(key) != expected) false else { saveState(key, next); true }
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
        require(Files.size(file) in 1..MAX_PROFILE_SOURCE_BYTES)
        require(Files.size(file) <= MAX_PROFILE_MEDIA_BYTES) { "L’image originale dépasse 18 Mo ; aucune compression automatique n’est appliquée." }
        val header = Files.newInputStream(file).use { it.readNBytes(12) }
        val mime = requireNotNull(profileOriginalMime(header)) { "Format non pris en charge : choisissez JPEG, PNG, WebP ou GIF." }
        ProfileLocalImages.validateImageDimensions(file)
        ProfileImageBytes(mime, Files.readAllBytes(file))
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
