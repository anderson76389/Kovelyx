package com.nuvio.tv.ui.screens.profile

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Conserve durablement le fond personnel sur CETTE télévision.
 *
 * Les URI content:// du sélecteur ne sont pas durables : on recopie le fichier
 * dans le stockage privé de Kovelyx TV. Aucune donnée n'est envoyée aux anciens
 * serveurs Nuvio ; une synchronisation multiplateforme exigerait un vrai upload R2.
 */
internal fun importPrivateProfileBackground(context: Context, selected: Uri): String {
    val mime = context.contentResolver.getType(selected)?.lowercase()
        ?: throw IOException("Impossible de déterminer le format de cette image.")
    val extension = when (mime) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/avif" -> "avif"
        else -> throw IOException("Image non compatible : JPG, PNG, GIF, WebP ou HEIC.")
    }
    val folder = File(context.filesDir, "kovelyx-tv-profile-backgrounds")
    if (!folder.isDirectory && !folder.mkdirs()) {
        throw IOException("Impossible de créer le dossier des fonds personnalisés.")
    }
    val target = File(folder, "fond-${UUID.randomUUID()}.$extension")
    val pending = File.createTempFile("fond-", ".tmp", folder)
    try {
        val source = context.contentResolver.openInputStream(selected)
            ?: throw IOException("Impossible de lire l'image choisie.")
        source.use { input ->
            pending.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    bytes += count
                    if (bytes > 18_000_000L) {
                        throw IOException("L'image dépasse la limite de 18 Mo.")
                    }
                    output.write(buffer, 0, count)
                }
                if (bytes == 0L) throw IOException("L'image sélectionnée est vide.")
            }
        }
        if (!pending.renameTo(target)) throw IOException("Impossible d'enregistrer le fond.")
        return Uri.fromFile(target).toString()
    } finally {
        pending.delete()
    }
}
