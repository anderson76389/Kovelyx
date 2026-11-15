package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Reclaims ONLY an AppImage backup explicitly marked by Kovelyx's updater.
 * Runs after the replacement app has already displayed its window for 30 seconds.
 * No directory traversal and no deletes in downloads, profiles or arbitrary data.
 */
internal object AppImageBackupMaintenance {
    const val OWNER_MARKER_CONTENT = "KOVELYX_APPIMAGE_PREVIOUS_V1"
    const val BACKUP_SUFFIX = ".kovelyx-previous"
    const val MARKER_SUFFIX = ".kovelyx-previous.owner"

    fun clean(runningAppImage: File): Long {
        if (!runningAppImage.isAbsolute ||
            !runningAppImage.name.endsWith(".AppImage", ignoreCase = true)
        ) return 0L

        val target = runningAppImage.absoluteFile
        val parent = target.parentFile ?: return 0L
        val backup = File(parent, target.name + BACKUP_SUFFIX)
        val marker = File(parent, target.name + MARKER_SUFFIX)

        fun regularOwnedFile(file: File): Boolean =
            Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                file.canonicalFile == file.absoluteFile

        return runCatching {
            // Reject symlinks (including an ancestor of the target), unexpected
            // backups and pre-migration backups that have no updater ownership marker.
            if (!regularOwnedFile(target) || !regularOwnedFile(backup) ||
                !regularOwnedFile(marker)
            ) return@runCatching 0L
            if (marker.readText(Charsets.UTF_8).trim() != OWNER_MARKER_CONTENT) return@runCatching 0L
            if (Files.isSameFile(target.toPath(), backup.toPath())) return@runCatching 0L
            if (!target.isAppImageElf() || !backup.isAppImageElf()) return@runCatching 0L

            val bytes = backup.length()
            if (backup.delete()) {
                // If deleting the marker fails, it is harmless: no backup remains.
                marker.delete()
                bytes
            } else 0L
        }.getOrDefault(0L)
    }

    private fun File.isAppImageElf(): Boolean =
        length() > 4 && inputStream().use { input ->
            input.read() == 0x7F && input.read() == 'E'.code &&
                input.read() == 'L'.code && input.read() == 'F'.code
        }
}
