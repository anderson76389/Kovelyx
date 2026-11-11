package com.kovelyx.app.features.updater

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/** Called only after the replacement application's window has survived startup. */
internal object PortableBackupMaintenance {
    fun clean(install: File, nowMs: Long = System.currentTimeMillis()): Long {
        fun linked(file: File): Boolean = Files.isSymbolicLink(file.toPath()) ||
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isOther
        val current = install.canonicalFile
        if (!File(current, "Kovelyx.portable").isFile || !File(current, "Kovelyx.exe").isFile) return 0L
        var reclaimed = 0L
        for (backup in current.parentFile?.listFiles().orEmpty()) {
            runCatching {
                if (!backup.name.matches(Regex("\\.kovelyx-previous-[0-9a-f]{32}"))) return@runCatching
                if (linked(backup) || backup.canonicalFile != backup.absoluteFile) return@runCatching
                val marker = File(backup, ".kovelyx-update-owner")
                val owner = marker.takeIf { it.isFile && !Files.isSymbolicLink(it.toPath()) }?.readText()?.trim()
                if (owner != null && !owner.equals(current.path, ignoreCase = true)) return@runCatching
                if (owner == null && nowMs - backup.lastModified() < 300_000L) return@runCatching
                if (!File(backup, "Kovelyx.portable").isFile) return@runCatching
                val cfg = File(backup, "app/Kovelyx.cfg")
                if (!cfg.isFile || cfg.canonicalFile != cfg.absoluteFile) return@runCatching
                if (!Regex("app.mainclass=(com\\.kovelyx|com\\.nuvio)\\.app\\.MainKt").containsMatchIn(cfg.readText())) return@runCatching
                // Retain a backup if another process is still using it. A denied process
                // enumeration conservatively prevents cleanup instead of risking rollback.
                val running = ProcessHandle.allProcesses().use { processes ->
                    processes.anyMatch { process -> process.info().command().orElse("").startsWith(backup.path + File.separator, ignoreCase = true) }
                }
                if (running) return@runCatching
                // Delete managed binaries only. Notes, media, preferences and arbitrary
                // files added by the user remain, including those inside app/runtime.
                fun prune(file: File) {
                    if (!file.exists() || linked(file) || file.canonicalFile != file.absoluteFile) return
                    if (file.isDirectory) {
                        file.listFiles().orEmpty().forEach(::prune)
                        if (file.listFiles()?.isEmpty() == true) file.delete()
                    } else {
                        val relative = file.relativeTo(backup).invariantSeparatorsPath
                        val binary = relative == "Kovelyx.exe" ||
                            relative == "runtime/lib/modules" ||
                            ((relative.startsWith("app/") || relative.startsWith("runtime/")) &&
                                file.name.matches(Regex(".*\\.(jar|dll|dylib|pdb|so(?:\\.[0-9]+)*)", RegexOption.IGNORE_CASE)))
                        if (binary) { val size = file.length(); if (file.delete()) reclaimed += size }
                    }
                }
                listOf("Kovelyx.exe", "app", "runtime").forEach { prune(File(backup, it)) }
            }
        }
        return reclaimed
    }
}
