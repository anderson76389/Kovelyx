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

    @Test fun displayPreferenceUsesOnlySupported60Or120HzWithoutChangingResolution() {
        val activity = AtomicReference<android.app.Activity>()
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.SideEffect { activity.set(context as android.app.Activity) }
        }
        val original = AtomicReference<android.view.Display.Mode>()
        compose.runOnIdle {
            val host = activity.get()
            original.set(requireNotNull(host.window.decorView.display).mode)
            com.kovelyx.display.AndroidDisplayRefresh.bind(host)
            com.kovelyx.display.DisplayRefreshSettings.select(120)
        }
        compose.waitForIdle()
        try {
            compose.runOnIdle {
                val host = activity.get()
                val modes = host.window.decorView.display.supportedModes.toList()
                val sameSize = modes.filter { it.physicalWidth == original.get().physicalWidth && it.physicalHeight == original.get().physicalHeight }
                val available = sameSize.mapNotNull { com.kovelyx.display.nominalRefreshRate(it.refreshRate) }.toSet()
                val chosen = modes.firstOrNull { it.modeId == host.window.attributes.preferredDisplayModeId }
                if (available.isNotEmpty()) {
                    org.junit.Assert.assertNotNull("A supported display mode must be requested", chosen)
                    val expected = if (120 in available) 120 else 60
                    org.junit.Assert.assertEquals(expected, com.kovelyx.display.nominalRefreshRate(requireNotNull(chosen).refreshRate))
                    org.junit.Assert.assertEquals(original.get().physicalWidth, chosen.physicalWidth)
                    org.junit.Assert.assertEquals(original.get().physicalHeight, chosen.physicalHeight)
                }
            }
        } finally {
            compose.runOnIdle { com.kovelyx.display.DisplayRefreshSettings.select(60) }
        }
    }

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
