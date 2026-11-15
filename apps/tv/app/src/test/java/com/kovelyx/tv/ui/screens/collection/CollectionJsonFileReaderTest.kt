package com.kovelyx.tv.ui.screens.collection

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue

class CollectionJsonFileReaderTest {
    private fun expectReaderFailure(action: () -> Unit) {
        var failed = false
        try {
            action()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("The JSON reader must reject invalid input", failed)
    }

    @Test
    fun utf8JsonAndBomAreReadWithoutChangingCollections() {
        val payload = "\uFEFF[{\"id\":\"fr\",\"title\":\"Cinéma français\",\"folders\":[]}]"
        assertEquals(payload.removePrefix("\uFEFF"), readCollectionsJson(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun emptyAndNonUtf8FilesAreRejected() {
        expectReaderFailure {
            readCollectionsJson(ByteArrayInputStream(byteArrayOf()))
        }
        expectReaderFailure {
            readCollectionsJson(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test
    fun filesOverSixteenMiBAreRejectedBeforeDecoding() {
        expectReaderFailure {
            readCollectionsJson(ByteArrayInputStream(ByteArray(MAX_COLLECTION_JSON_BYTES + 1) { 'a'.code.toByte() }))
        }
    }
}
