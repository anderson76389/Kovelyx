package com.kovelyx.app.features.player

import java.awt.Desktop
import java.io.File
import java.net.URI

private data class DesktopExternalPlayerIntent(
    val request: ExternalPlayerPlaybackRequest,
    val playerId: String?,
)

internal actual object ExternalPlayerPlatform {
    private const val systemPlayerId = "system"

    actual fun defaultPlayerId(): String? = installedDesktopPlayers().firstOrNull()?.id ?: systemPlayerId

    actual fun availablePlayers(): List<ExternalPlayerApp> =
        installedDesktopPlayers().map { ExternalPlayerApp(it.id, it.name) } +
            ExternalPlayerApp(systemPlayerId, "Application par défaut (fichiers locaux)")

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult {
        val installed = installedDesktopPlayers()
        val player = if (playerId == null || playerId == systemPlayerId) {
            installed.firstOrNull { desktopExternalPlayerCommand(it, request) != null }
        } else {
            installed.firstOrNull { it.id == playerId }
                ?: return ExternalPlayerOpenResult.NoPlayerAvailable
        }
        if (player != null) {
            val command = desktopExternalPlayerCommand(player, request)
                ?: return ExternalPlayerOpenResult.Failed
            return if (runCatching {
                ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }.isSuccess) ExternalPlayerOpenResult.Opened else ExternalPlayerOpenResult.Failed
        }
        // HTTP associations launch a browser, not a video player. Never report that as playback.
        val uri = runCatching { URI(request.sourceUrl) }.getOrNull()
            ?: return ExternalPlayerOpenResult.Failed
        if (!uri.scheme.equals("file", ignoreCase = true)) return ExternalPlayerOpenResult.NoPlayerAvailable
        return if (openUri(request.sourceUrl)) ExternalPlayerOpenResult.Opened else ExternalPlayerOpenResult.Failed
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
