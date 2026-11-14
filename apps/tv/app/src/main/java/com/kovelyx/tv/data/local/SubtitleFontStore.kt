package com.kovelyx.tv.data.local

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Device-local subtitle fonts. No server, extension or Nuvio service is involved.
 * The only persisted custom identifier is a generated filename, never an arbitrary path.
 */
internal object SubtitleFontStore {
    const val DEFAULT = "default"
    const val SANS_SERIF = "sans-serif"
    const val SERIF = "serif"
    const val MONOSPACE = "monospace"
    private const val MAX_BYTES = 10L * 1024L * 1024L
    private val customId = Regex("^custom:[0-9a-f\\-]{36}\\.(?:ttf|otf)$")

    fun isSupportedChoice(choice: String): Boolean =
        choice == DEFAULT || choice == SANS_SERIF || choice == SERIF ||
            choice == MONOSPACE || customId.matches(choice)

    fun label(choice: String): String = when (choice) {
        DEFAULT -> "Par défaut"
        SANS_SERIF -> "Sans empattement"
        SERIF -> "Avec empattement"
        MONOSPACE -> "Monospace"
        else -> if (customId.matches(choice)) "Police importée" else "Par défaut"
    }

    private fun directory(context: Context): File = File(context.filesDir, "subtitle-fonts")

    fun file(context: Context, choice: String): File? {
        if (!customId.matches(choice)) return null
        val name = choice.removePrefix("custom:")
        val candidate = File(directory(context), name)
        return candidate.takeIf { it.isFile && it.length() in 4..MAX_BYTES }
    }

    fun fontName(context: Context, choice: String): String = when (choice) {
        SERIF -> "serif"
        MONOSPACE -> "monospace"
        else -> file(context, choice)?.let(::sfntFamilyName) ?: "sans-serif"
    }

    fun fontsDirectory(context: Context): String = directory(context).absolutePath

    fun resolve(context: Context, choice: String, bold: Boolean): Typeface {
        val base = when (choice) {
            SANS_SERIF -> Typeface.create("sans-serif", Typeface.NORMAL)
            SERIF -> Typeface.SERIF
            MONOSPACE -> Typeface.MONOSPACE
            else -> file(context, choice)?.let {
                runCatching { Typeface.createFromFile(it) }.getOrNull()
            } ?: Typeface.DEFAULT
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Copy at most 10 MiB from a document provider; verify SFNT header and Android parser. */
    fun import(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: throw IllegalArgumentException("Fichier sans nom")
        val suffix = when {
            name.endsWith(".ttf", ignoreCase = true) -> ".ttf"
            name.endsWith(".otf", ignoreCase = true) -> ".otf"
            else -> throw IllegalArgumentException("Utilise une police .ttf ou .otf")
        }
        val dir = directory(context)
        check(dir.exists() || dir.mkdirs()) { "Impossible de créer le dossier des polices" }
        val file = File(dir, UUID.randomUUID().toString() + suffix)
        try {
            val source = resolver.openInputStream(uri) ?: error("Impossible de lire la police")
            var copied = 0L
            source.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        require(copied <= MAX_BYTES) { "Police trop grande (10 Mo maximum)" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            require(copied >= 4) { "Police vide" }
            val validHeader = file.inputStream().use { stream ->
                val four = ByteArray(4)
                stream.read(four) == 4 && (
                    four.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                        String(four, Charsets.US_ASCII) in setOf("OTTO", "true", "typ1")
                    )
            }
            require(validHeader) { "Fichier de police invalide" }
            Typeface.createFromFile(file) // Reject corrupt files before saving the preference.
            return "custom:${file.name}"
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }
}
