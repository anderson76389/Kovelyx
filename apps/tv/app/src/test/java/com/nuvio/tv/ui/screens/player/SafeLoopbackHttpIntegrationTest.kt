package com.nuvio.tv.ui.screens.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Une VRAIE socket HTTP : contrairement au test simulé, vérifie que le chemin
 * de lecture utilisé par Kovelyx TV remonte le vrai statut HTTP du serveur.
 */
class SafeLoopbackHttpIntegrationTest {
    @Test
    fun realLocalhost404IsNotChangedTo400() {
        ServerSocket(0).use { socket ->
            socket.soTimeout = 5000
            val worker = thread(isDaemon = true, name = "kovelyx-404-http-test") {
                runCatching {
                    socket.accept().use { client ->
                        client.soTimeout = 5000
                        val request = client.getInputStream().bufferedReader()
                        while (true) {
                            val line = request.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val response = "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(response.toByteArray(Charsets.US_ASCII))
                            flush()
                        }
                    }
                }
            }
            val uri = mockk<Uri>()
            every { uri.scheme } returns "http"
            every { uri.host } returns "127.0.0.1"
            every { uri.toString() } returns "http://127.0.0.1:${socket.localPort}/missing.mp4"

            val normal = mockk<DataSource>(relaxed = true)
            val http = OkHttpDataSource.Factory(OkHttpClient()).createDataSource()
            val source = SafeLoopbackDataSource(http, normal)
            try {
                val error = assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) {
                    source.open(DataSpec.Builder().setUri(uri).build())
                }
                assertEquals(404, error.responseCode)
                verify(exactly = 0) { normal.open(any()) }
            } finally {
                source.close()
                socket.close()
                worker.join(5000)
            }
        }
    }
}
