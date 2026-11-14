package com.kovelyx.app.features.player

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object AndroidSubtitleFontStore {
    private const val MAX_BYTES = 10L * 1024L * 1024L

    fun file(context: Context, choice: String): File? {
        val name = SubtitleFontChoice.filename(choice) ?: return null
        return File(File(context.filesDir, "subtitle-fonts"), name)
            .takeIf { it.isFile && it.length() in 4..MAX_BYTES }
    }

    fun fontName(context: Context, choice: String): String = when (choice) {
        SubtitleFontChoice.SERIF -> "serif"
        SubtitleFontChoice.MONOSPACE -> "monospace"
        else -> file(context, choice)?.let(::sfntFamilyName) ?: "sans-serif"
    }

    fun typeface(context: Context, choice: String, bold: Boolean): Typeface {
        val base = when (choice) {
            SubtitleFontChoice.SANS_SERIF -> Typeface.create("sans-serif", Typeface.NORMAL)
            SubtitleFontChoice.SERIF -> Typeface.SERIF
            SubtitleFontChoice.MONOSPACE -> Typeface.MONOSPACE
            else -> file(context, choice)?.let {
                runCatching { Typeface.createFromFile(it) }.getOrNull()
            } ?: Typeface.DEFAULT
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun import(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: throw IllegalArgumentException("Fichier sans nom")
        val ext = when {
            name.endsWith(".ttf", true) -> ".ttf"
            name.endsWith(".otf", true) -> ".otf"
            else -> throw IllegalArgumentException("Utilise un fichier .ttf ou .otf")
        }
        val dir = File(context.filesDir, "subtitle-fonts")
        check(dir.exists() || dir.mkdirs()) { "Stockage indisponible" }
        val target = File(dir, UUID.randomUUID().toString() + ext)
        try {
            val source = resolver.openInputStream(uri) ?: error("Impossible de lire ce fichier")
            var total = 0L
            source.use { input ->
                FileOutputStream(target).use { output ->
                    val bytes = ByteArray(8192)
                    while (true) {
                        val size = input.read(bytes)
                        if (size < 0) break
                        total += size
                        require(total <= MAX_BYTES) { "Police trop volumineuse (10 Mo maximum)" }
                        output.write(bytes, 0, size)
                    }
                }
            }
            require(total >= 4) { "Fichier vide" }
            val valid = target.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    (header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                        String(header, Charsets.US_ASCII) in setOf("OTTO", "true", "typ1"))
            }
            require(valid) { "Format de police invalide" }
            Typeface.createFromFile(target)
            return "custom:${target.name}"
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }
}

@Composable
internal actual fun SubtitleFontImportButton(onImported: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { AndroidSubtitleFontStore.import(context, uri) } }
                .onSuccess(onImported)
                .onFailure { Toast.makeText(context, it.message ?: "Police non valide", Toast.LENGTH_LONG).show() }
        }
    }
    Text(
        text = "Importer une police (.ttf / .otf)",
        modifier = Modifier.clickable { launcher.launch(arrayOf("*/*")) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
    )
}
