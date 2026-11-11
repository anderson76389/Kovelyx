package com.kovelyx.app.features.updater

import java.io.File
import java.util.Locale

/** Only the updater's private directory; never an app-data or media tree. */
internal object UpdateCacheMaintenance {
    private const val UNKNOWN_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private val archiveSuffixes = listOf(".apk", ".msi", ".exe", ".zip", ".appimage", ".deb", ".rpm")

    fun clean(
        directory: File,
        isAlreadyInstalled: (File) -> Boolean?,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        if (directory.name != "updates" || !directory.isDirectory) return 0L
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return 0L
        if (root.name != "updates" || root.parentFile != directory.parentFile?.canonicalFile) return 0L
        var deletedBytes = 0L
        for (file in directory.listFiles().orEmpty()) {
            runCatching {
                if (!file.isFile || file.canonicalFile != File(root, file.name)) return@runCatching
                val name = file.name.lowercase(Locale.ROOT)
                val owned = name.startsWith("kovelyx-") || name.startsWith("nuvio-") || name.startsWith("nuvio_")
                if (!owned) return@runCatching
                val partial = name.endsWith(".part") && archiveSuffixes.any { name.endsWith(it + ".part") }
                val archive = archiveSuffixes.any { name.endsWith(it) }
                val remove = when {
                    partial -> nowMs - file.lastModified() >= 24L * 60 * 60 * 1000 // Also protect another desktop process.
                    !archive -> false
                    else -> when (runCatching { isAlreadyInstalled(file) }.getOrNull()) {
                        true -> true
                        false -> false // A newer APK/installer remains usable after cancellation.
                        null -> nowMs - file.lastModified() >= UNKNOWN_MAX_AGE_MS
                    }
                }
                if (remove) {
                    val size = file.length()
                    if (file.delete()) deletedBytes += size
                }
            }
        }
        return deletedBytes
    }
}
