package com.kovelyx.android

import android.util.Base64
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kovelyx.app.core.ui.NuvioTheme
import com.kovelyx.app.features.player.AndroidPlaybackEngine
import com.kovelyx.app.features.player.PlatformPlayerSurface
import com.kovelyx.app.features.player.PlayerPlaybackSnapshot
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

    @Test fun notificationTestActuallyReachesAndroidWithoutAnySavedSeries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        // This app module exercises the library's real platform implementation.
        for (name in listOf("EpisodeReleaseNotificationsStorage", "EpisodeReleaseNotificationPlatform")) {
            val type = Class.forName("com.kovelyx.app.features.notifications.$name")
            type.getMethod("initialize", android.content.Context::class.java).invoke(type.getField("INSTANCE").get(null), context)
        }
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancelAll()
        try {
            com.kovelyx.app.features.notifications.EpisodeReleaseNotificationsRepository.sendTestNotification()
            compose.waitUntil(15_000) { manager.activeNotifications.any {
                it.notification.channelId == "episode_release_notifications" &&
                    it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.isNotBlank() == true
            } }
            assertTrue("The platform must report a sent notification", manager.activeNotifications.isNotEmpty())
            compose.waitUntil(5_000) {
                com.kovelyx.app.features.notifications.EpisodeReleaseNotificationsRepository.uiState.value.statusMessage != null
            }
        } finally { manager.cancelAll() }
    }

    @Test fun exoPlayerSurfaceReadsH264() = play(AndroidPlaybackEngine.ExoPlayer)
    @Test fun libmpvSurfaceReadsH264() = play(AndroidPlaybackEngine.Libmpv)

    @Test fun displayRangeUsesOnlySupportedModesUpTo144HzWithoutChangingResolution() {
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
            com.kovelyx.display.DisplayRefreshSettings.selectRange(1, 144)
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
                    val expected = available.maxOrNull()!!
                    org.junit.Assert.assertEquals(expected, com.kovelyx.display.nominalRefreshRate(requireNotNull(chosen).refreshRate))
                    val stored = host.applicationContext.getSharedPreferences("kovelyx_display_refresh", 0)
                    org.junit.Assert.assertEquals(1, stored.getInt("minimum_hertz", -1))
                    org.junit.Assert.assertEquals(144, stored.getInt("maximum_hertz", -1))
                    org.junit.Assert.assertEquals(original.get().physicalWidth, chosen.physicalWidth)
                    org.junit.Assert.assertEquals(original.get().physicalHeight, chosen.physicalHeight)
                }
            }
        } finally {
            compose.runOnIdle { com.kovelyx.display.DisplayRefreshSettings.selectRange(60, 60) }
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
