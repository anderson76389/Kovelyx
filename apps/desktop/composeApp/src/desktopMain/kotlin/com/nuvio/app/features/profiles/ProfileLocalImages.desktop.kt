package com.nuvio.app.features.profiles

import com.nuvio.app.core.storage.DesktopStorage
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local, private Windows images. This does NOT upload photographs to Legacy.
 * The file chooser accepts common raster formats, validates decoded dimensions
 * and stores an unchanged copy inside %APPDATA%/Kovelyx/profile-media.
 */
internal actual object ProfileLocalImages {
    actual val supported: Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val imageDir: Path
        get() = DesktopStorage.rootDir.resolve("profile-media").toAbsolutePath().normalize()

    private val settings by lazy { DesktopStorage.store("kovelyx_profile_media") }

    actual suspend fun chooseImage(background: Boolean): String? {
        if (!supported) return null
        return withContext(Dispatchers.IO) {
            var selectedPath: Path? = null
            // Swing modal dialogs must run on AWT's event-dispatch thread.
            SwingUtilities.invokeAndWait {
                val chooser = JFileChooser().apply {
                    dialogTitle = if (background) "Choisir un fond de profil" else "Choisir une photo de profil"
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    isMultiSelectionEnabled = false
                    isAcceptAllFileFilterUsed = true
                    fileFilter = FileNameExtensionFilter("Images (JPG, PNG, WebP, GIF)", "jpg", "jpeg", "png", "webp", "gif")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selectedPath = chooser.selectedFile?.toPath()
                }
            }

            val original = selectedPath ?: return@withContext null
            require(Files.isRegularFile(original) && Files.size(original) in 1..com.kovelyx.cloud.media.MAX_PROFILE_SOURCE_BYTES) {
                "L’image doit être un fichier de 64 Mo maximum avant optimisation."
            }
            require(Files.size(original) <= com.kovelyx.cloud.media.MAX_PROFILE_MEDIA_BYTES) { "L’image originale dépasse 18 Mo ; aucune compression automatique n’est appliquée." }
            val mime = Files.newInputStream(original).use {
                requireNotNull(com.kovelyx.cloud.media.profileOriginalMime(it.readNBytes(12))) {
                    "Format non pris en charge : choisissez JPEG, PNG, WebP ou GIF."
                }
            }
            validateImageDimensions(original)
            val outputExtension = when (mime) {
                "image/jpeg" -> ".jpg"
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                else -> ".gif"
            }
            val target = imageDir.resolve(
                (if (background) "background-" else "avatar-") +
                    UUID.randomUUID().toString() + outputExtension
            )
            Files.createDirectories(imageDir)
            val pending = Files.createTempFile(imageDir, "kovelyx-import-", ".tmp")
            try {
                Files.copy(original, pending, StandardCopyOption.REPLACE_EXISTING)
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(pending)
            }
            target.toUri().toString()
        }
    }

    private fun imageInput(path: Path): javax.imageio.stream.ImageInputStream {
        val original = requireNotNull(ImageIO.createImageInputStream(path.toFile())) { "Impossible de lire l’image." }
        if (ImageIO.getImageReaders(original).hasNext()) return original
        original.close()
        val bytes = Files.readAllBytes(path)
        val png = org.jetbrains.skia.Image.makeFromEncoded(bytes).use { image ->
            require(image.width > 0 && image.height > 0 && image.width.toLong() * image.height <= 32_000_000L) { "Image supérieure à 32 mégapixels." }
            requireNotNull(image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)).use { it.bytes }
        }
        return requireNotNull(ImageIO.createImageInputStream(java.io.ByteArrayInputStream(png))) { "Format d’image non pris en charge." }
    }

    internal fun validateImageDimensions(path: Path) {
        imageInput(path).use { stream ->
            requireNotNull(stream) { "Impossible de lire l'image." }
            val readers = ImageIO.getImageReaders(stream)
            require(readers.hasNext()) { "Format d'image non reconnu." }
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000L) {
                    "Dimensions d'image non prises en charge (32 mégapixels maximum)."
                }
            } finally {
                reader.dispose()
            }
        }
    }

    internal fun readAndResize(path: Path, background: Boolean): BufferedImage {
        imageInput(path).use { stream ->
            requireNotNull(stream) { "Impossible de lire l'image." }
            val readers = ImageIO.getImageReaders(stream)
            require(readers.hasNext()) { "Format d'image non reconnu." }
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(width > 0 && height > 0 && width.toLong() * height <= 32_000_000L) {
                    "Dimensions d'image non prises en charge (32 mégapixels maximum)."
                }
                val maxSide = if (background) 1920 else 512
                val ratio = minOf(1.0, maxSide.toDouble() / maxOf(width, height))
                val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
                val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
                val subsample = maxOf(1, (maxOf(width, height) / maxSide) / 2)
                val params = reader.defaultReadParam
                params.setSourceSubsampling(subsample, subsample, 0, 0)
                val source = reader.read(0, params)
                val output = BufferedImage(
                    targetWidth,
                    targetHeight,
                    if (background) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB,
                )
                val graphics: Graphics2D = output.createGraphics()
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
                } finally {
                    graphics.dispose()
                    source.flush()
                }
                return output
            } finally {
                reader.dispose()
            }
        }
    }

    actual fun isManagedUri(url: String): Boolean {
        if (!supported || !url.startsWith("file:", ignoreCase = true)) return false
        return runCatching {
            val path = Path.of(URI(url)).toRealPath()
            val cloudDir = DesktopStorage.rootDir.resolve("cloud-profile-media").toAbsolutePath().normalize()
            (path.startsWith(imageDir) || path.startsWith(cloudDir)) && Files.isRegularFile(path) &&
                (path.fileName.toString().lowercase(Locale.ROOT).endsWith(".png") ||
                    path.fileName.toString().lowercase(Locale.ROOT).endsWith(".jpg") ||
                    path.fileName.toString().lowercase(Locale.ROOT).endsWith(".webp") ||
                    path.fileName.toString().lowercase(Locale.ROOT).endsWith(".gif"))
        }.getOrDefault(false)
    }

    private fun key(userId: String, profileIndex: Int, background: Boolean): String {
        val owner = MessageDigest.getInstance("SHA-256")
            .digest(userId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "$owner.$profileIndex." + if (background) "background" else "avatar"
    }

    actual fun savedImage(userId: String, profileIndex: Int, background: Boolean): String? =
        settings.getString(key(userId, profileIndex, background))?.takeIf(::isManagedUri)

    actual fun saveImage(userId: String, profileIndex: Int, background: Boolean, url: String?) {
        if (!supported) return
        settings.putString(
            key(userId, profileIndex, background),
            url?.takeIf(::isManagedUri),
        )
    }
}
