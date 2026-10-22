package com.nuvio.app.features.library

import com.nuvio.app.features.library.sync.LibrarySyncKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudflareLibraryCodecTest {
    private fun item(id: String = "tt0001") = LibraryItem(
        id=id, type="movie", name="Film",
        poster="https://image.tmdb.org/t/p/w500/cover.jpg",
        addonBaseUrl="https://example.invalid/?access_token=secret",
        savedAtEpochMs=123L
    )

    @Test
    fun onlyPublicLibraryMetadataIsSentToCloud() {
        val payload=CloudflareLibraryCodec.payload(item()).toString()
        assertTrue("tt0001" in payload)
        assertTrue("image.tmdb.org" in payload)
        assertFalse("access_token" in payload)
        assertFalse("example.invalid" in payload)
        assertFalse("secret" in payload)
    }

    @Test
    fun tombstoneIsParsedWithoutItemData() {
        val row=Json.parseToJsonElement(
            """{"itemKey":"movie:tt0001","revision":3,"deleted":true,"payload":{}}"""
        ).jsonObject
        val parsed=CloudflareLibraryCodec.decode(listOf(row)).getValue("movie:tt0001")
        assertEquals(3,parsed.revision)
        assertTrue(parsed.deleted)
        assertEquals(null,parsed.item)
    }

    @Test
    fun malformedRemoteKeyCannotBeImported() {
        val row=Json.parseToJsonElement(
            """{"itemKey":"movie:wrong","revision":1,"deleted":false,"payload":{"item":{"id":"tt0001","type":"movie","name":"Film","savedAtEpochMs":1}}}"""
        ).jsonObject
        assertFails { CloudflareLibraryCodec.decode(listOf(row)) }
    }

    @Test
    fun additiveMergePreservesUnrelatedLocalTitlesAndLegacyCursor() {
        val state=LibraryLocalState()
        val token=state.beginProfileLoad(1).snapshot.token
        state.completeProfileLoad(token,1,items=listOf(item()),deltaCursorEventId=50L,deltaInitialized=true)
        val before=state.snapshot()
        val cloud=LibraryItem(id="tt0002",type="movie",name="Autre",savedAtEpochMs=456L)
        val updated=assertNotNull(state.applyCloudItems(before,listOf(cloud),emptySet()))
        assertEquals(setOf("tt0001","tt0002"),updated.items.map { it.id }.toSet())
        assertEquals(50L,updated.deltaCursorEventId)
        assertTrue(updated.deltaInitialized)
    }

    @Test
    fun pendingLocalChangeCannotBeDeletedByCloudTombstone() {
        val state=LibraryLocalState()
        val token=state.beginProfileLoad(1).snapshot.token
        state.completeProfileLoad(token,1,items=emptyList())
        val new=state.upsert(item())
        val merged=assertNotNull(state.applyCloudItems(new,emptyList(),setOf("movie:tt0001")))
        assertEquals(1,merged.items.size)
        assertEquals(1,merged.pendingUpsertKeys.size)
    }

    @Test
    fun cloudCheckpointSurvivesLibrarySerialization() {
        val state=LibraryLocalState()
        val token=state.beginProfileLoad(1).snapshot.token
        val before=assertNotNull(state.completeProfileLoad(token,1,items=listOf(item())))
        val checkpoint=CloudLibraryCheckpoint("user-one","profile-one",mapOf("movie:tt0001" to 4))
        val saved=LibraryStoragePayloadCodec.decode(LibraryStoragePayloadCodec.encode(before,checkpoint))
        assertEquals(checkpoint,saved.cloudCheckpoint)
        assertEquals("tt0001",saved.items.single().id)
    }
}
