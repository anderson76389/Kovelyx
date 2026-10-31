package com.kovelyx.cloud.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
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
        require(file.length() in 1..18_000_000L) { "Image locale vide ou supérieure à 18 Mo." }
        val header = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
        if (String(header, 0, 6, Charsets.US_ASCII).startsWith("GIF8")) {
            require(file.length() <= MAX_PROFILE_MEDIA_BYTES) { "Le GIF animé dépasse 2 Mio ; choisissez une animation plus légère pour la synchroniser." }
            return@withContext ProfileImageBytes("image/gif", file.readBytes())
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) {
            "Dimensions d’image non prises en charge (32 mégapixels maximum)."
        }
        val target = if (background) 1920 else 512
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val oriented = orient(decoded, file)
        var bitmap = oriented
        try {
            val ratio = minOf(1.0, target.toDouble() / maxOf(oriented.width, oriented.height))
            if (ratio < 1.0) bitmap = Bitmap.createScaledBitmap(oriented,
                (oriented.width * ratio).toInt().coerceAtLeast(1), (oriented.height * ratio).toInt().coerceAtLeast(1), true)
            val output = ByteArrayOutputStream()
            val png = !background && bitmap.hasAlpha()
            check(bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, output))
            val bytes = output.toByteArray()
            require(bytes.size <= MAX_PROFILE_MEDIA_BYTES) { "Image trop volumineuse après optimisation." }
            ProfileImageBytes(if (png) "image/png" else "image/jpeg", bytes)
        } finally {
            if (bitmap !== oriented) bitmap.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }
    private fun orient(source: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        }.getOrDefault(1)
        val matrix = android.graphics.Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(-90f)
        }
        return if (matrix.isIdentity) source else Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
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
