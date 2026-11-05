package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopExternalPlayerCommandTest {
    @Test
    fun vlcResumeUsesSecondsAndPreservesStreamUrl() {
        val command = desktopPlayerCommand(
            executable = "vlc",
            playerId = "vlc",
            request = ExternalPlayerPlaybackRequest(
                sourceUrl = "https://example.org/video.m3u8?token=a&language=fr",
                title = "Un film",
                resumePositionMs = 125_500L,
            ),
        )
        assertEquals(
            listOf("vlc", "--play-and-exit", "--start-time=125.5", "https://example.org/video.m3u8?token=a&language=fr"),
            command,
        )
    }

    @Test
    fun mpvDoesNotInterpretStreamUrlAsAnOption() {
        val command = desktopPlayerCommand(
            executable = "mpv",
            playerId = "mpv",
            request = ExternalPlayerPlaybackRequest(
                sourceUrl = "https://example.org/episode.mp4",
                title = "Une série",
                resumePositionMs = 90_000L,
            ),
        )
        assertEquals(
            listOf("mpv", "--force-window=yes", "--start=90.0", "--", "https://example.org/episode.mp4"),
            command,
        )
    }

    @Test
    fun unsupportedSchemesAndPlayersAreRejected() {
        val request = ExternalPlayerPlaybackRequest("javascript:alert(1)", "Test")
        assertNull(desktopPlayerCommand("vlc", "vlc", request))
        assertNull(desktopPlayerCommand("vlc", "unknown", request.copy(sourceUrl = "https://example.org/test.mp4")))
        assertTrue(desktopPlayerCommand("vlc", "vlc", request.copy(sourceUrl = "file:///tmp/film.mkv"))!!.contains("file:///tmp/film.mkv"))
    }
}
