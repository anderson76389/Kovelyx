package com.nuvio.app

import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCollectionImportBridgeTest {
    @Test
    fun trustedLocalImportStaysPendingUntilUserConfirms() {
        DesktopCollectionImportBridge.start()
        assertTrue(DesktopCollectionImportBridge.alreadyRunning())

        val blocked = post("https://untrusted.example", "[{\"id\":\"test\"}]")
        assertEquals(403, blocked)
        assertEquals(null, DesktopCollectionImportBridge.pending.value)

        val allowed = post(
            "https://anderson76389.github.io",
            "[{\"id\":\"test\",\"title\":\"Test\",\"folders\":[]}]",
        )
        assertEquals(202, allowed)
        assertNotNull(DesktopCollectionImportBridge.pending.value)
        assertEquals(409, post("https://anderson76389.github.io", "[]"))

        DesktopCollectionImportBridge.pending.value?.let(DesktopCollectionImportBridge::discard)
        assertEquals(null, DesktopCollectionImportBridge.pending.value)
    }

    private fun post(origin: String, body: String): Int {
        val connection = (URL("http://127.0.0.1:38761/import").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 1000
        connection.readTimeout = 1000
        connection.setRequestProperty("Origin", origin)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
