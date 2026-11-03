package com.kovelyx.cloud.library
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
class CloudLibraryMetadataTest {
    @Test fun equalTimestampBackfillPreservesConfiguredUrlsAndRemoteMetadata() {
        val old = Json.parseToJsonElement("""{"id":"custom:42","savedAtEpochMs":123,"name":"Original","poster":null,"genres":[]}""").jsonObject
        val pc = Json.parseToJsonElement("""{"id":"custom:42","savedAtEpochMs":123,"name":"Different","poster":"https://img.example/Poster?token=AbC&width=900","addonBaseUrl":"https://addon.example/Config?key=CaseSensitive","genres":["Drama"],"posterShape":"Landscape"}""").jsonObject
        val received = enrichLibraryMetadata(old, pc)
        assertEquals("Original", received["name"]!!.jsonPrimitive.content)
        assertEquals(pc["poster"], received["poster"])
        assertEquals(pc["addonBaseUrl"], received["addonBaseUrl"])
        assertEquals(pc["genres"], received["genres"])
        assertEquals(received, enrichLibraryMetadata(received, pc))
    }
    @Test fun missingMetadataDoesNotEraseAnotherDevicesFields() {
        val remote = Json.parseToJsonElement("""{"logo":"https://img.example/Logo?token=X","futureOption":true}""").jsonObject
        val empty = Json.parseToJsonElement("""{"logo":null}""").jsonObject
        assertEquals(remote, enrichLibraryMetadata(remote, empty))
        assertTrue(enrichLibraryMetadata(remote, empty)["futureOption"]!!.jsonPrimitive.boolean)
    }
}
