package com.kovelyx.cloud.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class AndroidProfileMediaFiles(context: Context) : ProfileMediaFiles {
    private val context = context.applicationContext
    private val state = this.context.getSharedPreferences("kovelyx_cloud_profile_media", Context.MODE_PRIVATE)
    private val folder = File(this.context.filesDir, "kovelyx-cloud-profile-media")
    private val json = Json { ignoreUnknownKeys = true }
    private val stateLock = Any()
    private fun stateKey(key: String) = sha256(key.toByteArray())
    override fun loadState(key: String): ProfileMediaState? = synchronized(stateLock) {
        state.getString(stateKey(key), null)?.let { json.decodeFromString<ProfileMediaState>(it) }
    }
    override fun saveState(key: String, state: ProfileMediaState) = synchronized(stateLock) {
        this.state.edit().putString(stateKey(key), json.encodeToString(state)).apply()
    }
    override fun compareAndSaveState(key: String, expected: ProfileMediaState?, next: ProfileMediaState): Boolean = synchronized(stateLock) {
        if (loadState(key) != expected) false else { saveState(key, next); true }
    }
    private fun privateFile(uri: String): File {
        require(uri.startsWith("file:")) { "Le fichier de profil doit être une copie locale privée." }
        val file = File(requireNotNull(Uri.parse(uri).path)).canonicalFile
        val root = context.filesDir.canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Fichier de profil hors du stockage privé." }
        return file
    }
    override fun exists(uri: String) = runCatching { privateFile(uri).isFile }.getOrDefault(false)
    override fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    override suspend fun encodeLocal(uri: String, background: Boolean): ProfileImageBytes = withContext(Dispatchers.IO) {
        val file = privateFile(uri)
        require(file.length() in 1..MAX_PROFILE_SOURCE_BYTES) { "Image locale vide ou supérieure à 64 Mo avant optimisation." }
        require(file.length() <= MAX_PROFILE_MEDIA_BYTES) { "L’image originale dépasse 18 Mo ; aucune compression automatique n’est appliquée." }
        val header = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
        val mime = requireNotNull(profileOriginalMime(header)) { "Format non pris en charge : choisissez JPEG, PNG, WebP ou GIF." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) {
            "Dimensions d’image non prises en charge (32 mégapixels maximum)."
        }
        ProfileImageBytes(mime, file.readBytes())
    }
    override suspend fun saveDownloaded(key: String, version: String, image: ProfileImageBytes): String = withContext(Dispatchers.IO) {
        require(version.matches(Regex("[a-f0-9]{64}")))
        check(folder.isDirectory || folder.mkdirs())
        val prefix = stateKey(key) + "-"
        val extension = when(image.mime) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> error("Type d’image distant invalide") }
        val file = File(folder, prefix + version + "." + extension)
        val temp = File.createTempFile("download-", ".tmp", folder)
        try { temp.writeBytes(image.bytes); check(temp.renameTo(file)) { "Impossible de conserver l’image téléchargée." } }
        finally { temp.delete() }
        folder.listFiles()?.filter { it.name.startsWith(prefix) && it != file }?.forEach { it.delete() }
        Uri.fromFile(file).toString()
    }
}
