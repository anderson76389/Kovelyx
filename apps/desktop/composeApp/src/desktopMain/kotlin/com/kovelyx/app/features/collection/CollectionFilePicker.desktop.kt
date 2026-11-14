package com.kovelyx.app.features.collection

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

internal actual val collectionFilePickerAvailable = true
@Composable
internal actual fun rememberCollectionFilePicker(onSelected: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val failed by rememberUpdatedState(onError)
    var choosing by remember { mutableStateOf(false) }
    return {
        if (!choosing) {
            choosing = true
            scope.launch {
                try {
                    val file = withContext(Dispatchers.Swing) {
                        val dialog = FileDialog(null as Frame?, "Importer une collection JSON", FileDialog.LOAD)
                        try {
                            dialog.setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
                            dialog.isVisible = true
                            dialog.file?.let { File(dialog.directory, it) }
                        } finally { dialog.dispose() }
                    }
                    if (file != null) {
                        val text = withContext(Dispatchers.IO) {
                            require(file.length() in 1..MAX_COLLECTION_FILE_BYTES.toLong())
                            file.inputStream().use { input ->
                                val bytes = input.readNBytes(MAX_COLLECTION_FILE_BYTES + 1)
                                require(bytes.size <= MAX_COLLECTION_FILE_BYTES)
                                bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
                            }
                        }
                        selected(text)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed("Impossible de lire le fichier JSON (UTF-8, 16 Mo maximum).") }
                finally { choosing = false }
            }
        }
    }
}
