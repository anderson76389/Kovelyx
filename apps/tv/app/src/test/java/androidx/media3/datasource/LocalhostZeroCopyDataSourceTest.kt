package androidx.media3.datasource

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.ServerSocket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class LocalhostZeroCopyDataSourceTest {

    @Test
    fun testOpenAndReadZeroCopy() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val expectedData = "Hello, zero-copy socket loopback data pipeline!"
        val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: ${expectedData.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        thread {
            try {
                val client = serverSocket.accept()
                val reader = client.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }
                val out = client.getOutputStream()
                out.write(responseHeaders.toByteArray())
                out.write(expectedData.toByteArray())
                out.flush()
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "127.0.0.1"
        every { mockUri.port } returns port
        every { mockUri.path } returns "/stream.mp4"
        every { mockUri.query } returns null

        val dataSource = LocalhostZeroCopyDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(mockUri)
            .build()
        
        try {
            val bytesRemaining = dataSource.open(dataSpec)
            assertEquals(expectedData.length.toLong(), bytesRemaining)
            assertTrue(dataSource.supportsByteBufferRead())

            val buffer = ByteBuffer.allocateDirect(100)
            val bytesRead = dataSource.read(buffer, expectedData.length)
            assertEquals(expectedData.length, bytesRead)
            
            buffer.flip()
            val bytes = ByteArray(bytesRead)
            buffer.get(bytes)
            val actualData = String(bytes)
            assertEquals(expectedData, actualData)
        } finally {
            dataSource.close()
            serverSocket.close()
        }
    }

    @Test
    fun testCaseInsensitiveContentLengthAndPartialContent() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val expectedData = "Partial payload"
        val responseHeaders = "HTTP/1.1 206 Partial Content\r\n" +
                "cOnTeNt-LeNgTh: ${expectedData.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"

        thread {
            try {
                val client = serverSocket.accept()
                val reader = client.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }
                val out = client.getOutputStream()
                out.write(responseHeaders.toByteArray())
                out.write(expectedData.toByteArray())
                out.flush()
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "127.0.0.1"
        every { mockUri.port } returns port
        every { mockUri.path } returns "/stream.mp4"
        every { mockUri.query } returns null

        val dataSource = LocalhostZeroCopyDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(mockUri)
            .build()
        
        try {
            val bytesRemaining = dataSource.open(dataSpec)
            assertEquals(expectedData.length.toLong(), bytesRemaining)

            val buffer = ByteBuffer.allocateDirect(100)
            val bytesRead = dataSource.read(buffer, expectedData.length)
            assertEquals(expectedData.length, bytesRead)
            
            buffer.flip()
            val bytes = ByteArray(bytesRead)
            buffer.get(bytes)
            assertEquals(expectedData, String(bytes))
        } finally {
            dataSource.close()
            serverSocket.close()
        }
    }

    @Test
    fun testExcessBytesHandling() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        // 15 bytes of body
        val expectedData = "BufferLookAhead"
        val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: ${expectedData.length}\r\n" +
                "\r\n"

        thread {
            try {
                val client = serverSocket.accept()
                val out = client.getOutputStream()
                val fullPayload = responseHeaders.toByteArray() + expectedData.toByteArray()
                out.write(fullPayload)
                out.flush()
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "127.0.0.1"
        every { mockUri.port } returns port
        every { mockUri.path } returns "/stream.mp4"
        every { mockUri.query } returns null

        val dataSource = LocalhostZeroCopyDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(mockUri)
            .build()
        
        try {
            val bytesRemaining = dataSource.open(dataSpec)
            assertEquals(expectedData.length.toLong(), bytesRemaining)

            val buffer = ByteBuffer.allocateDirect(100)
            val bytesRead = dataSource.read(buffer, expectedData.length)
            assertEquals(expectedData.length, bytesRead)
            
            buffer.flip()
            val bytes = ByteArray(bytesRead)
            buffer.get(bytes)
            assertEquals(expectedData, String(bytes))
        } finally {
            dataSource.close()
            serverSocket.close()
        }
    }

    @Test
    fun testHttpError404() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        val responseHeaders = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"

        thread {
            try {
                val client = serverSocket.accept()
                // Respecter le protocole HTTP : attendre la ligne de requête
                // et les en-têtes avant d'envoyer la réponse 404.
                val reader = client.getInputStream().bufferedReader()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }
                val out = client.getOutputStream()
                out.write(responseHeaders.toByteArray())
                out.flush()
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mockUri = mockk<Uri>()
        every { mockUri.host } returns "127.0.0.1"
        every { mockUri.port } returns port
        every { mockUri.path } returns "/stream.mp4"
        every { mockUri.query } returns null

        val dataSource = LocalhostZeroCopyDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(mockUri)
            .build()
        
        try {
            val failure = assertThrows(HttpDataSource.HttpDataSourceException::class.java) {
                dataSource.open(dataSpec)
            }
            // Vérifier la réponse 404 même si le transport enveloppe l'exception.
            val code = generateSequence(failure as Throwable) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()?.responseCode
            assertEquals(404, code)
        } finally {
            dataSource.close()
            serverSocket.close()
        }
    }

    @Test
    fun testOtherHttpErrorStatusCodesStayIntact() {
        for (status in listOf(403, 416, 500, 503)) {
            ServerSocket(0).use { server ->
                server.soTimeout = 5000
                val port = server.localPort
                val worker = thread(isDaemon = true, name = "kovelyx-http-$status") {
                    runCatching {
                        server.accept().use { client ->
                            client.soTimeout = 5000
                            val input = client.getInputStream().bufferedReader()
                            while (true) {
                                val line = input.readLine()
                                if (line.isNullOrEmpty()) break
                            }
                            val response = "HTTP/1.1 $status Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            client.getOutputStream().apply {
                                write(response.toByteArray(Charsets.US_ASCII))
                                flush()
                            }
                        }
                    }
                }
                val uri = mockk<Uri>()
                every { uri.host } returns "127.0.0.1"
                every { uri.port } returns port
                every { uri.path } returns "/missing.mp4"
                every { uri.query } returns null
                val source = LocalhostZeroCopyDataSource()
                try {
                    val failure = assertThrows(HttpDataSource.HttpDataSourceException::class.java) {
                        source.open(DataSpec.Builder().setUri(uri).build())
                    }
                    val actual = generateSequence(failure as Throwable) { it.cause }
                        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    assertEquals("HTTP $status doit être conservé", status, actual)
                } finally {
                    source.close()
                    server.close()
                    worker.join(5000)
                }
            }
        }
    }

}
