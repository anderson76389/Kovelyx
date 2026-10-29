package com.nuvio.app.features.addons

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RawHttpBinaryDesktopTest {
    @Test fun `raw HTTP preserves private image bytes and still enforces response limits`() = runBlocking {
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0, 0xff.toByte(), 0x80.toByte(), 1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/private-image") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, image.size.toLong())
            exchange.responseBody.use { it.write(image) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/private-image"
            val complete = httpRequestRaw("GET", url, emptyMap(), "", maxResponseBodyBytes = 100)
            assertContentEquals(image, complete.bodyBytes)
            val limited = httpRequestRaw("GET", url, emptyMap(), "", maxResponseBodyBytes = 4)
            assertContentEquals(image.take(4).toByteArray(), limited.bodyBytes)
            assertTrue(limited.body.endsWith("...[truncated]"))
        } finally { server.stop(0) }
    }
}
