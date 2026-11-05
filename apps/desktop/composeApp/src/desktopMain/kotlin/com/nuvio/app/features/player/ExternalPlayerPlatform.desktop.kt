package com.nuvio.app.features.player

import java.awt.Desktop
import java.io.File
import java.net.URI

private data class DesktopExternalPlayerIntent(
    val request: ExternalPlayerPlaybackRequest,
    val playerId: String?,
)

internal actual object ExternalPlayerPlatform {
    private const val systemPlayerId = "system"
    private const val vlcPlayerId = "vlc"
    private const val mpvPlayerId = "mpv"

    actual fun defaultPlayerId(): String? = systemPlayerId

    // Show an explicit player only when it is installed. Opening a network URL
    // with Desktop.browse() may launch a browser instead of a video player.
    actual fun availablePlayers(): List<ExternalPlayerApp> = buildList {
        add(ExternalPlayerApp(systemPlayerId, "Lecteur par défaut"))
        if (findPlayerExecutable(vlcPlayerId) != null) add(ExternalPlayerApp(vlcPlayerId, "VLC"))
        if (findPlayerExecutable(mpvPlayerId) != null) add(ExternalPlayerApp(mpvPlayerId, "mpv"))
    }

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult {
        val launched = when (playerId) {
            null, systemPlayerId -> openUri(request.sourceUrl)
            vlcPlayerId, mpvPlayerId -> launchPlayer(request, playerId)
            else -> false
        }
        return if (launched) ExternalPlayerOpenResult.Opened else ExternalPlayerOpenResult.Failed
    }

    actual fun buildIntent(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerIntentResult =
        ExternalPlayerIntentResult.Success(DesktopExternalPlayerIntent(request, playerId))

    internal fun launch(intent: Any): Boolean {
        val desktopIntent = intent as? DesktopExternalPlayerIntent ?: return false
        return open(desktopIntent.request, desktopIntent.playerId) == ExternalPlayerOpenResult.Opened
    }

    private fun launchPlayer(request: ExternalPlayerPlaybackRequest, playerId: String): Boolean {
        // An external process cannot report progress back to Kovelyx. Resume is
        // forwarded, but playback completion must not be guessed on Windows.
        val executable = findPlayerExecutable(playerId) ?: return false
        val command = desktopPlayerCommand(executable, playerId, request) ?: return false
        return runCatching { ProcessBuilder(command).start() }.isSuccess
    }

    private fun findPlayerExecutable(playerId: String): String? {
        val windows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)
        val basename = when (playerId) {
            vlcPlayerId -> "vlc"
            mpvPlayerId -> "mpv"
            else -> return null
        }
        val executableName = basename + if (windows) ".exe" else ""
        val pathDirectories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val knownWindowsLocations = if (windows) {
            buildList {
                listOfNotNull(System.getenv("PROGRAMFILES"), System.getenv("PROGRAMFILES(X86)"))
                    .forEach { root ->
                        if (playerId == vlcPlayerId) add(File(root, "VideoLAN/VLC/vlc.exe"))
                        else add(File(root, "mpv/mpv.exe"))
                    }
                System.getenv("LOCALAPPDATA")?.let { root ->
                    if (playerId == vlcPlayerId) add(File(root, "Programs/VLC/vlc.exe"))
                    else add(File(root, "Programs/mpv/mpv.exe"))
                }
            }
        } else emptyList()
        val candidates = knownWindowsLocations +
            pathDirectories.filter { it.isNotBlank() }.map { File(it, executableName) }
        return candidates.firstOrNull { it.isFile }?.absolutePath
    }

    private fun openUri(rawUri: String): Boolean {
        val uri = runCatching { URI(rawUri) }.getOrNull() ?: return false
        val desktop = runCatching { Desktop.getDesktop() }.getOrNull()

        if (desktop != null && Desktop.isDesktopSupported()) {
            val opened = runCatching {
                if (uri.scheme.equals("file", ignoreCase = true)) {
                    desktop.open(File(uri))
                } else {
                    desktop.browse(uri)
                }
            }.isSuccess
            if (opened) return true
        }

        return openWithPlatformCommand(rawUri)
    }

    private fun openWithPlatformCommand(rawUri: String): Boolean {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            osName.contains("mac") -> listOf("open", rawUri)
            osName.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", rawUri)
            else -> listOf("xdg-open", rawUri)
        }
        return runCatching { ProcessBuilder(command).start() }.isSuccess
    }
}

/** Build a safe command without shell interpolation or interpreting the URL as options. */
internal fun desktopPlayerCommand(
    executable: String,
    playerId: String,
    request: ExternalPlayerPlaybackRequest,
): List<String>? {
    val url = request.sourceUrl.trim()
    val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    if (scheme !in setOf("http", "https", "file")) return null
    val seconds = (request.resumePositionMs.coerceAtLeast(0L) / 1000.0).toString()
    return when (playerId) {
        "vlc" -> buildList {
            add(executable)
            add("--play-and-exit")
            if (request.resumePositionMs > 0L) add("--start-time=$seconds")
            add(url)
        }
        "mpv" -> buildList {
            add(executable)
            add("--force-window=yes")
            if (request.resumePositionMs > 0L) add("--start=$seconds")
            add("--")
            add(url)
        }
        else -> null
    }
}
