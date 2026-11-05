package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopExternalPlayerCommandsTest {
    private val source = "https://example.test/video.mkv?token=a&name=film"
    private val vlc = InstalledDesktopPlayer("vlc", "VLC", "C:/Program Files/VideoLAN/VLC/vlc.exe")
    private val mpv = InstalledDesktopPlayer("mpv", "mpv", "C:/Program Files/mpv/mpv.exe")

    @Test
    fun vlcReceivesStreamResumeAndSubtitlesAsSeparateArguments() {
        val command = desktopExternalPlayerCommand(vlc, ExternalPlayerPlaybackRequest(
            sourceUrl = source, title = "Film", resumePositionMs = 125_500,
            sourceHeaders = mapOf("User-Agent" to "Kovelyx", "Referer" to "https://example.test/"),
            subtitles = listOf(SubtitleInput("https://example.test/fr.srt", "Français", "fr")),
        ))!!
        assertEquals(vlc.executable, command.first())
        assertEquals(source, command.last())
        assertEquals("--", command[command.lastIndex - 1])
        assertTrue("--start-time=125.5" in command)
        assertTrue("--http-user-agent=Kovelyx" in command)
        assertTrue("--http-referrer=https://example.test/" in command)
        assertTrue("--sub-file=https://example.test/fr.srt" in command)
        assertFalse(command.any { it == "cmd" || it == "rundll32" })
    }

    @Test
    fun authenticationHeadersArePreservedByMpvAndNeverSilentlyDroppedByVlc() {
        val request = ExternalPlayerPlaybackRequest(source, "Film", sourceHeaders = mapOf("Authorization" to "Bearer test-only"))
        assertNull(desktopExternalPlayerCommand(vlc, request))
        val command = desktopExternalPlayerCommand(mpv, request)!!
        assertTrue("--http-header-fields=Authorization: Bearer test-only" in command)
        assertEquals(source, command.last())
    }

    @Test
    fun invalidSchemesAndHeaderLineBreaksCannotBecomePlayerCommands() {
        assertNull(desktopExternalPlayerCommand(mpv, ExternalPlayerPlaybackRequest("--script=bad", "Film")))
        assertNull(desktopExternalPlayerCommand(mpv, ExternalPlayerPlaybackRequest("javascript:bad", "Film")))
        assertNull(desktopExternalPlayerCommand(mpv, ExternalPlayerPlaybackRequest(source, "Film", sourceHeaders = mapOf("X-Test" to "a\r\nb"))))
    }
}
