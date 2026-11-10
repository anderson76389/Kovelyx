package com.kovelyx.tv.ui.screens.player

import android.graphics.ImageFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kovelyx.tv.data.local.PlayerSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Offline engine soak. Uses production native allocator/budget, not the full player UI.
 * A drained private surface exercises decoding without claiming physical-TV GPU performance.
 */
@RunWith(AndroidJUnit4::class)
class Media3PlaybackSoakTest {
    @Test
    fun thirtyMinutesWithPauseSeekAndEpisodeChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val requestedDuration = args.getString("soakSeconds")
        assumeTrue("Long soak requires the dedicated workflow and explicit duration", requestedDuration != null)
        val seconds = checkNotNull(requestedDuration).toLong()
        require(seconds == 1800L) { "At least 30 minutes of wall-clock playback session required" }
        val folder = File(context.getExternalFilesDir(null), "playback-soak").apply { mkdirs() }
        val reportFile = File(folder, "report.json")
        val fixture = File(context.cacheDir, "playback-soak-720p.mp4")
        instrumentation.context.assets.open("playback-soak.mp4").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(fixture.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val expected = instrumentation.context.assets.open("playback-soak.sha256")
            .bufferedReader().use { it.readText().trim().substringBefore(' ') }
        assertEquals("Fixture changed after CI generation", expected, hash)
        val frames = AtomicInteger()
        val failure = AtomicReference<PlaybackException?>()
        val drain = HandlerThread("kovelyx-soak-output").apply { start() }
        val surface = ImageReader.newInstance(1280, 720, ImageFormat.PRIVATE, 4)
        surface.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.let { image ->
                frames.incrementAndGet()
                image.close()
            }
        }, Handler(drain.looper))
        val oldEnabled = KovelyxExoPlayerPerformanceHelper.enabled
        var player: ExoPlayer? = null
        val samples = JSONArray()
        val events = JSONArray()
        val report = JSONObject().put("passed", false)
            .put("scenario", "30-minute local H264 720p30 engine session, 3 playlist episodes")
            .put("limitations", "Unminified instrumentation; private output surface; not full player UI, physical TV, 4K or remote addons")
            .put("fixture_sha256", hash).put("fixture_bytes", fixture.length())
            .put("configured_seconds", seconds).put("samples", samples).put("events", events)
        fun save() { reportFile.writeText(report.toString(2) + "\n") }
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun ensurePlaying() {
            val deadline = SystemClock.elapsedRealtime() + 15_000L
            var playing = false
            while (SystemClock.elapsedRealtime() < deadline) {
                main { playing = player?.isPlaying == true }
                failure.get()?.let { throw AssertionError("Playback failed", it) }
                if (playing) return
                Thread.sleep(100)
            }
            throw AssertionError("Playback did not resume within 15 seconds")
        }
        var cpuBefore = Process.getElapsedCpuTime()
        var wallBefore = SystemClock.elapsedRealtime()
        fun sample(elapsed: Long) {
            val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            val cpu = Process.getElapsedCpuTime()
            val wall = SystemClock.elapsedRealtime()
            val item = JSONObject().put("elapsed_ms", elapsed)
                .put("pss_kib", memory.totalPss).put("native_pss_kib", memory.nativePss)
                .put("java_pss_kib", memory.dalvikPss).put("native_heap_bytes", Debug.getNativeHeapAllocatedSize())
                .put("decoded_frames", frames.get())
                .put("cpu_percent_one_core", (cpu - cpuBefore) * 100.0 / (wall - wallBefore).coerceAtLeast(1))
            main {
                val active = checkNotNull(player)
                item.put("episode", active.currentMediaItemIndex).put("position_ms", active.currentPosition)
                item.put("playing", active.isPlaying)
            }
            samples.put(item)
            cpuBefore = cpu
            wallBefore = wall
            save()
            android.util.Log.i("KovelyxPlaybackSoak", item.toString())
        }
        try {
            main {
                KovelyxExoPlayerPerformanceHelper.enabled = true
                KovelyxExoPlayerPerformanceHelper.updateSettings(
                    PlayerSettings(nuvioPerformanceModeEnabled = true), context)
                player = ExoPlayer.Builder(context)
                    .setLoadControl(KovelyxExoPlayerPerformanceHelper.buildLoadControl(context))
                    .build().apply {
                        setVideoSurface(surface.surface)
                        volume = 0f
                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) { failure.set(error) }
                        })
                        setMediaItems((0..2).map { index ->
                            MediaItem.Builder().setMediaId("soak-episode-$index")
                                .setUri(Uri.fromFile(fixture)).build()
                        })
                        repeatMode = Player.REPEAT_MODE_ALL
                        prepare()
                        play()
                    }
            }
            ensurePlaying()
            val firstFrameDeadline = SystemClock.elapsedRealtime() + 15_000L
            while (frames.get() == 0 && SystemClock.elapsedRealtime() < firstFrameDeadline) Thread.sleep(100)
            assertTrue("No initial decoded video frame", frames.get() > 0)
            main {
                val format = checkNotNull(player?.videoFormat)
                assertEquals("video/avc", format.sampleMimeType)
                assertEquals(1280, format.width)
                assertEquals(720, format.height)
                assertTrue(checkNotNull(player).duration in 29_000L..31_000L)
            }
            val start = SystemClock.elapsedRealtime()
            cpuBefore = Process.getElapsedCpuTime()
            wallBefore = start
            var nextSample = 60_000L
            var nextPause = 120_000L
            var nextSeek = 240_000L
            var nextEpisode = 300_000L
            var lastFrames = frames.get()
            sample(0)
            while (SystemClock.elapsedRealtime() - start < seconds * 1_000L) {
                val elapsed = SystemClock.elapsedRealtime() - start
                failure.get()?.let { throw AssertionError("Decoder failure during soak", it) }
                if (elapsed >= nextPause) {
                    main { checkNotNull(player).pause(); assertFalse(checkNotNull(player).playWhenReady) }
                    val pauseStart = SystemClock.elapsedRealtime()
                    val pauseCpu = Process.getElapsedCpuTime()
                    Thread.sleep(5_000)
                    events.put(JSONObject().put("action", "pause-resume").put("elapsed_ms", elapsed)
                        .put("pause_cpu_percent_one_core", (Process.getElapsedCpuTime() - pauseCpu) * 100.0 /
                            (SystemClock.elapsedRealtime() - pauseStart)))
                    main { checkNotNull(player).play() }
                    ensurePlaying()
                    nextPause += 300_000L
                }
                if (elapsed >= nextSeek) {
                    main { checkNotNull(player).seekTo(15_000L) }
                    ensurePlaying()
                    events.put(JSONObject().put("action", "seek-15s").put("elapsed_ms", elapsed))
                    nextSeek += 300_000L
                }
                if (elapsed >= nextEpisode) {
                    var expectedIndex = 0
                    main {
                        val active = checkNotNull(player)
                        expectedIndex = (active.currentMediaItemIndex + 1) % 3
                        active.seekToNextMediaItem()
                    }
                    ensurePlaying()
                    main { assertEquals(expectedIndex, checkNotNull(player).currentMediaItemIndex) }
                    events.put(JSONObject().put("action", "next-episode").put("elapsed_ms", elapsed))
                    nextEpisode += 300_000L
                }
                if (elapsed >= nextSample) {
                    ensurePlaying()
                    assertTrue("No decoder output since the last sample", frames.get() > lastFrames)
                    lastFrames = frames.get()
                    sample(elapsed)
                    nextSample += 60_000L
                }
                Thread.sleep(250)
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            sample(elapsed)
            assertTrue(elapsed >= 1_800_000L)
            assertTrue("No sustained decoder output", frames.get() > 6_000)
            val values = (0 until samples.length()).map { samples.getJSONObject(it) }
            fun median(rows: List<JSONObject>, key: String): Int = rows.map { it.getInt(key) }.sorted().let { it[it.size / 2] }
            val warm = values.filter { it.getLong("elapsed_ms") in 300_000L..600_000L }
            val tail = values.takeLast(5)
            val growth = median(tail, "pss_kib") - median(warm, "pss_kib")
            report.put("elapsed_ms", elapsed).put("pss_growth_after_warmup_kib", growth)
                .put("peak_pss_kib", values.maxOf { it.getInt("pss_kib") })
            assertTrue("Sustained PSS growth exceeds 64 MiB", growth <= 65_536)
            assertEquals(6, (0 until events.length()).count { events.getJSONObject(it).getString("action") == "pause-resume" })
            assertEquals(6, (0 until events.length()).count { events.getJSONObject(it).getString("action") == "seek-15s" })
            assertEquals(5, (0 until events.length()).count { events.getJSONObject(it).getString("action") == "next-episode" })
            report.put("passed", true)
        } finally {
            main {
                player?.release()
                KovelyxExoPlayerPerformanceHelper.liveAllocator = null
                KovelyxExoPlayerPerformanceHelper.updateSettings(PlayerSettings(), context)
                KovelyxExoPlayerPerformanceHelper.enabled = oldEnabled
            }
            surface.close()
            drain.quitSafely()
            fixture.delete()
            report.put("decoded_frames", frames.get())
            save()
        }
    }
}
