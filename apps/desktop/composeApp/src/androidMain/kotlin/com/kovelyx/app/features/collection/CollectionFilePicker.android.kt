package com.kovelyx.app.features.collection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*

internal actual val collectionFilePickerAvailable = true

@Composable
internal actual fun rememberCollectionFilePicker(
    onSelected: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val failed by rememberUpdatedState(onError)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= MAX_COLLECTION_FILE_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, MAX_COLLECTION_FILE_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        require(bytes.size <= MAX_COLLECTION_FILE_BYTES) { "Fichier trop volumineux (16 Mo maximum)." }
                        require(bytes.isNotEmpty()) { "Le fichier est vide." }
                        bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
                    } ?: error("Impossible d’ouvrir ce fichier.")
                }
                selected(text)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed("Impossible de lire le fichier JSON (UTF-8, 16 Mo maximum).") }
        }
    }
    return { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}
