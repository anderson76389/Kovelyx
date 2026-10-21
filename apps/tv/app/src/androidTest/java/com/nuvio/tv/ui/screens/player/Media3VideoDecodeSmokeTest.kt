package com.nuvio.tv.ui.screens.player

import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Actual Media3/MediaCodec device test. Replays a tiny 2-second 96x96 H.264 MP4
 * generated with FFmpeg's color source, entirely offline (no streaming provider).
 *
 * Confirms that the emulator decodes a real MP4 and reaches STATE_ENDED.
 * It does not certify physical TV hardware, codec performance, or rendering
 * through Kovelyx's on-screen player UI.
 */
@RunWith(AndroidJUnit4::class)
class Media3VideoDecodeSmokeTest {
    @Test
    fun h264Mp4ReachesEndWithoutDecoderErrors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val encoded = instrumentation.context.assets.open("media3-smoke.mp4.b64")
            .bufferedReader().use { it.readText().trim() }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        assertEquals("Unexpected MP4 test fixture size", 1660, bytes.size)
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(
            "The small offline MP4 fixture must not change silently",
            "566c872a74649ee8c04e9c43dccb4d17ec40ebdb4c365acd1bf5387ce3416cef",
            actualSha256
        )

        val mp4File = File(context.cacheDir, "kovelyx-media3-h264-smoke.mp4")
        mp4File.writeBytes(bytes)
        val done = CountDownLatch(1)
        val ready = AtomicBoolean(false)
        val videoSizeSeen = AtomicBoolean(false)
        val problem = AtomicReference<PlaybackException?>()
        var player: ExoPlayer? = null

        try {
            instrumentation.runOnMainSync {
                val activePlayer = ExoPlayer.Builder(context).build()
                player = activePlayer
                activePlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.set(true)
                        if (state == Player.STATE_ENDED) done.countDown()
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width == 96 && videoSize.height == 96) {
                            videoSizeSeen.set(true)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        problem.set(error)
                        done.countDown()
                    }
                })
                activePlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(mp4File)))
                activePlayer.prepare()
                activePlayer.play()
            }

            val finished = done.await(35, TimeUnit.SECONDS)
            assertTrue("H.264 playback did not finish within 35 seconds: ${problem.get()}", finished)
            assertNull("Media3 decoder or extractor reported an error", problem.get())
            assertTrue("Media3 never reached READY", ready.get())
            assertTrue("H.264 video dimensions were not recognized", videoSizeSeen.get())
            instrumentation.runOnMainSync {
                val activePlayer = checkNotNull(player)
                assertEquals("video/avc", activePlayer.videoFormat?.sampleMimeType)
                assertTrue("Unexpected video duration", activePlayer.duration in 1_800L..2_500L)
                assertEquals(Player.STATE_ENDED, activePlayer.playbackState)
            }
        } finally {
            player?.let { activePlayer ->
                instrumentation.runOnMainSync { activePlayer.release() }
            }
            mp4File.delete()
        }
    }
}
