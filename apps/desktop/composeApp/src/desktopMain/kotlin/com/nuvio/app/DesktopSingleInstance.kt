package com.nuvio.app

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Per-user process lock and private launch mailbox; no public HTTP activation endpoint. */
internal class DesktopSingleInstance(private val directory: Path) : AutoCloseable {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var watcher: java.nio.file.WatchService? = null
    @Volatile private var closed = false

    fun acquireOrForward(args: Array<String>, onLaunch: (Array<String>) -> Unit): Boolean {
        Files.createDirectories(directory)
        val opened = FileChannel.open(directory.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val acquired = try { opened.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
        if (acquired == null) {
            opened.close()
            val payload = Json.encodeToString(args.take(16))
            require(payload.toByteArray().size <= 65536) { "Arguments de lancement trop volumineux." }
            val pending = Files.createTempFile(directory, "launch-", ".tmp")
            try {
                Files.writeString(pending, payload)
                Files.move(pending, directory.resolve("launch-${UUID.randomUUID()}.json"))
            } finally { Files.deleteIfExists(pending) }
            return false
        }
        channel = opened
        lock = acquired
        val events = directory.fileSystem.newWatchService()
        watcher = events
        directory.register(events, StandardWatchEventKinds.ENTRY_CREATE)
        Thread({
            fun drain() {
                Files.newDirectoryStream(directory, "launch-*.json").use { paths ->
                    for (path in paths) {
                        runCatching {
                            if (Files.size(path) <= 65536) {
                                val launch = Json.decodeFromString<List<String>>(Files.readString(path)).take(16).toTypedArray()
                                onLaunch(launch)
                            }
                        }
                        Files.deleteIfExists(path)
                    }
                }
            }
            try {
                drain() // Includes launches arriving between lock acquisition and watcher registration.
                while (!closed) {
                    val key = events.take()
                    key.pollEvents()
                    drain()
                    if (!key.reset()) break
                }
            } catch (_: java.nio.file.ClosedWatchServiceException) {
                // Normal application shutdown.
            }
        }, "kovelyx-launch-mailbox").apply { isDaemon = true; start() }
        return true
    }

    override fun close() {
        closed = true
        watcher?.close()
        lock?.release()
        channel?.close()
        watcher = null
        lock = null
        channel = null
    }
}
