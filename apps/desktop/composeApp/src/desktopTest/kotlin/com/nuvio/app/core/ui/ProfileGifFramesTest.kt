package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.toPixelMap
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProfileGifFramesTest {
    @Test fun `profile GIF renderer advances through different frames and preserves delays`() {
        val file = Files.createTempFile("kovelyx-profile-animation-", ".gif")
        try {
            Files.write(file, Base64.getDecoder().decode("R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQBFAABACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7"))
            ProfileGifFrames(file).use { frames ->
                assertEquals(2, frames.frameCount)
                assertEquals(100L, frames.durationMillis(0))
                assertEquals(200L, frames.durationMillis(1))
                val image = frames.frame(0)
                assertEquals(2, image.width)
                assertEquals(2, image.height)
                val first = image.toPixelMap()[0, 0]
                val second = frames.frame(1).toPixelMap()[0, 0]
                assertNotEquals(first, second)
            }
        } finally { Files.deleteIfExists(file) }
    }
}
