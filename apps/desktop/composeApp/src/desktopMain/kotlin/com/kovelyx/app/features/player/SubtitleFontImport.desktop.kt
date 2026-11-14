package com.kovelyx.app.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kovelyx.app.core.storage.DesktopStorage
import java.awt.Font
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object DesktopSubtitleFontStore {
    private const val MAX_BYTES = 10L * 1024L * 1024L
    private val directory: File get() = DesktopStorage.rootDir.resolve("subtitle-fonts").toFile()

    fun file(choice: String): File? {
        val filename = SubtitleFontChoice.filename(choice) ?: return null
        return File(directory, filename).takeIf { it.isFile && it.length() in 4..MAX_BYTES }
    }

    fun fontName(choice: String): String = when (choice) {
        SubtitleFontChoice.SERIF -> "serif"
        SubtitleFontChoice.MONOSPACE -> "monospace"
        SubtitleFontChoice.SANS_SERIF -> "sans-serif"
        else -> file(choice)?.let { runCatching {
            Font.createFont(Font.TRUETYPE_FONT, it).family
        }.getOrNull() } ?: "sans-serif"
    }

    fun fontsDirectory(): String = directory.absolutePath

    fun importFile(source: File): String {
        val ext = when {
            source.name.endsWith(".ttf", true) -> ".ttf"
            source.name.endsWith(".otf", true) -> ".otf"
            else -> throw IllegalArgumentException("Utilise une police .ttf ou .otf")
        }
        require(source.isFile && source.length() in 4..MAX_BYTES) { "Police trop grande (10 Mo maximum)" }
        check(directory.exists() || directory.mkdirs()) { "Dossier de polices indisponible" }
        val target = File(directory, UUID.randomUUID().toString() + ext)
        try {
            source.inputStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= MAX_BYTES) { "Police trop grande (10 Mo maximum)" }
                        output.write(buffer, 0, size)
                    }
                }
            }
            val valid = target.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    (header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                        String(header, Charsets.US_ASCII) in setOf("OTTO", "true", "typ1"))
            }
            require(valid) { "Fichier de police invalide" }
            Font.createFont(Font.TRUETYPE_FONT, target)
            return "custom:${target.name}"
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }
}

@Composable
internal actual fun SubtitleFontImportButton(onImported: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Text(
        text = "Importer une police (.ttf / .otf)",
        modifier = Modifier.clickable {
            val chooser = JFileChooser().apply {
                dialogTitle = "Importer une police de sous-titres"
                fileFilter = FileNameExtensionFilter("Polices TrueType et OpenType", "ttf", "otf")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val source = chooser.selectedFile
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { DesktopSubtitleFontStore.importFile(source) }
                    }.onSuccess(onImported)
                }
            }
        }.padding(vertical = 12.dp, horizontal = 8.dp)
    )
}
