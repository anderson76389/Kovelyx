package com.nuvio.android

import android.util.Base64
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.player.AndroidPlaybackEngine
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real application surfaces on a disposable device; no addon, account or external server. */
@RunWith(AndroidJUnit4::class)
class VideoPlaybackSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exoPlayerSurfaceReadsH264() = play(AndroidPlaybackEngine.ExoPlayer)
    @Test fun libmpvSurfaceReadsH264() = play(AndroidPlaybackEngine.Libmpv)

    private fun play(engine: AndroidPlaybackEngine) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("media3-smoke.mp4.b64").use {
            Base64.decode(it.readBytes(), Base64.DEFAULT)
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(sha == "566c872a74649ee8c04e9c43dccb4d17ec40ebdb4c365acd1bf5387ce3416cef")
        val fixture = File(instrumentation.targetContext.cacheDir, "playback-smoke.mp4")
        fixture.writeBytes(bytes)
        val latest = AtomicReference(PlayerPlaybackSnapshot())
        val failure = AtomicReference<String?>()
        val visible = mutableStateOf(true)
        try {
            compose.setContent {
                if (visible.value) NuvioTheme {
                    PlatformPlayerSurface(
                        sourceUrl = fixture.toURI().toASCIIString(),
                        modifier = Modifier.fillMaxSize(),
                        playbackEngine = engine,
                        onControllerReady = {},
                        onSnapshot = { latest.set(it) },
                        onError = { if (it != null) failure.set(it) },
                    )
                }
            }
            compose.waitUntil(timeoutMillis = 35_000) {
                failure.get() != null || latest.get().let {
                    it.positionMs >= 500 && it.durationMs >= 1800 && it.videoWidth > 0 && it.videoHeight > 0
                }
            }
            assertNull("$engine reported a playback error", failure.get())
            assertTrue("$engine never decoded the video dimensions", latest.get().videoWidth > 0)
            assertTrue("$engine never advanced the video", latest.get().positionMs >= 500)
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            fixture.delete()
        }
    }
}
