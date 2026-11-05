package com.nuvio.app.features.player

import java.io.File
import java.net.URI

internal data class InstalledDesktopPlayer(val id: String, val name: String, val executable: String)

internal fun installedDesktopPlayers(): List<InstalledDesktopPlayer> {
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
    fun find(name: String, locations: List<String>): String? =
        (locations + path.map { File(it, name).path }).firstOrNull { File(it).isFile }
    val roots = if (windows) listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")) else emptyList()
    val vlc = find(if (windows) "vlc.exe" else "vlc", roots.map { File(it, "VideoLAN/VLC/vlc.exe").path })
    val mpv = find(if (windows) "mpv.exe" else "mpv", roots.map { File(it, "mpv/mpv.exe").path })
    return listOfNotNull(
        vlc?.let { InstalledDesktopPlayer("vlc", "VLC", it) },
        mpv?.let { InstalledDesktopPlayer("mpv", "mpv", it) },
    )
}

/** Arguments are passed directly to the executable, never through a shell. */
internal fun desktopExternalPlayerCommand(
    player: InstalledDesktopPlayer,
    request: ExternalPlayerPlaybackRequest,
): List<String>? {
    val uri = runCatching { URI(request.sourceUrl) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https", "file", "rtsp", "rtmp")) return null
    if (request.sourceHeaders.any { (key, value) ->
            key.isBlank() || key.any { it == ':' || it == '\r' || it == '\n' } ||
                value.any { it == '\r' || it == '\n' }
        }) return null
    val seconds = (request.resumePositionMs.coerceAtLeast(0) / 1000.0).toString()
    return when (player.id) {
        "vlc" -> {
            // VLC has no generic request-header option; do not silently drop authentication.
            if (request.sourceHeaders.keys.any { it.lowercase() !in setOf("user-agent", "referer", "referrer") }) return null
            buildList {
                add(player.executable)
                add("--start-time=$seconds")
                request.sourceHeaders.forEach { (key, value) ->
                    add(if (key.equals("User-Agent", true)) "--http-user-agent=$value" else "--http-referrer=$value")
                }
                request.subtitles?.firstOrNull()?.let { add("--sub-file=${it.url}") }
                add("--")
                add(request.sourceUrl)
            }
        }
        "mpv" -> buildList {
            add(player.executable)
            add("--start=$seconds")
            if (request.sourceHeaders.isNotEmpty()) {
                fun escape(value: String) = value.replace("\\", "\\\\").replace(",", "\\,")
                add("--http-header-fields=" + request.sourceHeaders.entries.joinToString(",") { (key, value) -> escape("$key: $value") })
            }
            request.subtitles?.forEach { add("--sub-file=${it.url}") }
            add("--")
            add(request.sourceUrl)
        }
        else -> null
    }
}
