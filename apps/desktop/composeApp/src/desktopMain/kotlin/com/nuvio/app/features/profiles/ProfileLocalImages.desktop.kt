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
 * Local, private Windows images. This does NOT upload photographs to Supabase.
 * The file chooser accepts common raster formats, validates decoded dimensions
 * and stores an optimized copy inside %APPDATA%/Kovelyx/profile-media.
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
                    isAcceptAllFileFilterUsed = false
                    fileFilter = FileNameExtensionFilter("Images (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selectedPath = chooser.selectedFile?.toPath()
                }
            }

            val original = selectedPath ?: return@withContext null
            require(Files.isRegularFile(original) && Files.size(original) in 1..12_000_000L) {
                "L'image doit être un fichier de 12 Mo maximum."
            }
            // Conserver les GIF animés tels quels : l'ancien redimensionnement
            // ImageIO ne gardait que la première image de l'animation.
            val keepGif = original.fileName.toString().lowercase(Locale.ROOT).endsWith(".gif")
            if (keepGif) validateImageDimensions(original)
            val resized = if (keepGif) null else readAndResize(original, background)
            val outputExtension = if (keepGif) ".gif" else if (background) ".jpg" else ".png"
            val target = imageDir.resolve(
                (if (background) "background-" else "avatar-") +
                    UUID.randomUUID().toString() + outputExtension
            )
            Files.createDirectories(imageDir)
            val pending = Files.createTempFile(imageDir, "kovelyx-import-", ".tmp")
            try {
                if (keepGif) {
                    Files.copy(original, pending, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.newOutputStream(pending).use { output ->
                        check(ImageIO.write(requireNotNull(resized), if (background) "jpg" else "png", output)) {
                            "L'encodeur de l'image n'est pas disponible."
                        }
                    }
                }
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(pending)
            }
            target.toUri().toString()
        }
    }

    private fun validateImageDimensions(path: Path) {
        ImageIO.createImageInputStream(path.toFile()).use { stream ->
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
        ImageIO.createImageInputStream(path.toFile()).use { stream ->
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
            val path = Path.of(URI(url)).toAbsolutePath().normalize()
            path.startsWith(imageDir) && Files.isRegularFile(path) &&
                (path.fileName.toString().lowercase(Locale.ROOT).endsWith(".png") ||
                    path.fileName.toString().lowercase(Locale.ROOT).endsWith(".jpg") ||
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
