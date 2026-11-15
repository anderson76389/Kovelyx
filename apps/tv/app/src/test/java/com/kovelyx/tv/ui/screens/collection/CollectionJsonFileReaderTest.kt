package com.kovelyx.tv.ui.screens.collection

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class CollectionJsonFileReaderTest {
    @Test
    fun utf8JsonAndBomAreReadWithoutChangingCollections() {
        val payload = "\uFEFF[{\"id\":\"fr\",\"title\":\"Cinéma français\",\"folders\":[]}]"
        assertEquals(payload.removePrefix("\uFEFF"), readCollectionsJson(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun emptyAndNonUtf8FilesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            readCollectionsJson(ByteArrayInputStream(byteArrayOf()))
        }
        assertFailsWith<Exception> {
            readCollectionsJson(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test
    fun filesOverSixteenMiBAreRejectedBeforeDecoding() {
        assertFailsWith<IllegalArgumentException> {
            readCollectionsJson(ByteArrayInputStream(ByteArray(MAX_COLLECTION_JSON_BYTES + 1) { 'a'.code.toByte() }))
        }
    }
}
