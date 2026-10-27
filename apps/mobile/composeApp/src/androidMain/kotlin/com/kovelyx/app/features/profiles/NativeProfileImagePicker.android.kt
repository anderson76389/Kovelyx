package com.kovelyx.app.features.profiles

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual val nativeProfileImagePickerAvailable: Boolean = true

@Composable
internal actual fun rememberNativeProfileImagePicker(
    onSelected: (background: Boolean, savedUrl: String) -> Unit,
    onError: (String) -> Unit,
): (background: Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedHandler by rememberUpdatedState(onSelected)
    val errorHandler by rememberUpdatedState(onError)
    var choosingBackground by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val forBackground = choosingBackground
            scope.launch {
                try {
                    val url = withContext(Dispatchers.IO) {
                        copyPrivateImage(context, uri, forBackground)
                    }
                    selectedHandler(forBackground, url)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    errorHandler(error.message ?: "Impossible d'importer cette image.")
                }
            }
        }
    }
    return remember(picker) {
        { background: Boolean ->
            choosingBackground = background
            picker.launch("image/*")
        }
    }
}

/** Never store temporary content:// grants. Store an app-private durable copy. */
private fun copyPrivateImage(context: Context, source: Uri, background: Boolean): String {
    val resolver = context.contentResolver
    val mime = resolver.getType(source)?.lowercase()
        ?: throw IOException("Le type du fichier est inconnu.")
    val extension = when (mime) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        "image/avif" -> "avif"
        else -> throw IOException("Format non pris en charge : utilisez JPG, PNG, WebP, GIF ou HEIC.")
    }
    val folder = File(context.filesDir, "kovelyx-profile-media")
    check(folder.isDirectory || folder.mkdirs()) {
        "Impossible de créer le dossier des photos de profil."
    }
    val destination = File(
        folder,
        (if (background) "fond-" else "avatar-") + UUID.randomUUID().toString() + "." + extension
    )
    val temp = File.createTempFile("import-", ".tmp", folder)
    try {
        val sourceStream = resolver.openInputStream(source)
            ?: throw IOException("Impossible d'ouvrir la photo sélectionnée.")
        sourceStream.use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var size = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    if (size > 18_000_000L) {
                        throw IOException("L'image est trop volumineuse (maximum 18 Mo).")
                    }
                    output.write(buffer, 0, count)
                }
                if (size == 0L) throw IOException("Le fichier sélectionné est vide.")
            }
        }
        if (!temp.renameTo(destination)) {
            throw IOException("Impossible de conserver la photo sélectionnée.")
        }
        // Coil accepts this file URI. It persists across restarts of this app.
        return Uri.fromFile(destination).toString()
    } finally {
        temp.delete()
    }
}
