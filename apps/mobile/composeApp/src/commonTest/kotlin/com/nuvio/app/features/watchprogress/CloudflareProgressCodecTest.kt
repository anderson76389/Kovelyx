package com.nuvio.app.features.watchprogress

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class CloudflareProgressCodecTest {
    private fun entry(): WatchProgressEntry = WatchProgressEntry(
        contentType = "movie",
        parentMetaId = "603",
        parentMetaType = "movie",
        videoId = "603",
        title = "Film",
        lastPositionMs = 500L,
        durationMs = 2000L,
        lastUpdatedEpochMs = 10000L,
        poster = "https://evil.example/p.png?token=never-upload",
        lastSourceUrl = "https://stream.example/video.m3u8?token=secret-stream",
        trackingSourceUrl = "https://provider.example/secret",
        providerAddonId = "addon-personal"
    )

    @Test
    fun cloud_payload_strips_private_stream_and_provider_urls() {
        val payload = CloudflareWatchProgressCodec.payload(entry()).toString()
        assertFalse("secret-stream" in payload)
        assertFalse("never-upload" in payload)
        assertFalse("addon-personal" in payload)
        assertFalse("stream.example" in payload)
        assertFalse("provider.example" in payload)
    }

    @Test
    fun d1_roundtrip_preserves_progress_and_requires_matching_identity() {
        val progress = entry()
        val key = progress.resolvedProgressKey()
        val row = buildJsonObject {
            put("itemKey", key)
            put("revision", 1)
            put("deleted", false)
            put("payload", CloudflareWatchProgressCodec.payload(progress))
        }
        val restored = CloudflareWatchProgressCodec.decode(listOf(row)).getValue(key).entry!!
        assertEquals(500L, restored.lastPositionMs)
        assertEquals(2000L, restored.durationMs)
        assertEquals(null, restored.lastSourceUrl)
        val wrongId = buildJsonObject {
            put("itemKey", "invalid-identity")
            put("revision", 1)
            put("deleted", false)
            put("payload", CloudflareWatchProgressCodec.payload(progress))
        }
        assertFailsWith<IllegalArgumentException> {
            CloudflareWatchProgressCodec.decode(listOf(wrongId))
        }
    }

    @Test
    fun cloud_deletions_survive_app_restart() {
        val marker = CLOUD_DELETED_PROGRESS_PREFIX + "movie:603"
        val persisted = WatchProgressCodec.encodePayload(
            entries = emptyList(), lastSuccessfulPushEpochMs = 0L,
            deltaCursorEventId = 0L, deltaInitialized = false,
            dirtyProgressKeys = setOf(marker)
        )
        assertEquals(setOf(marker), WatchProgressCodec.decodePayload(persisted).dirtyProgressKeys)
    }
}
