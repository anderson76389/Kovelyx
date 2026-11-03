package com.kovelyx.nativecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioUploadMode
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceEngineTest {
    @Test
    fun releaseAarCreatesEngineAndExportsSystemTrust() {
        val root = newRoot("trust")
        val engine = createEngine(root)
        try {
            assertEquals("0.1.4", NuvioEngine.version)
            assertTrue(NuvioEngine.protocolBackendVersion.startsWith("2.0.12"))
            val bundle = File(root, "data/tls/android-ca.pem")
            assertTrue(bundle.readText().contains("-----BEGIN CERTIFICATE-----"))
        } finally {
            engine.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun releaseAarVerifiesTorrentAndServesFullAndRangeBytes() = runBlocking<Unit> {
        val root = newRoot("stream")
        val torrentId = "13ade5f13f4e7ce4021a3cc82f72e504b9ef35ac"
        val payload = File(root, "cache/payload/$torrentId/test.bin")
        payload.parentFile!!.mkdirs()
        payload.writeBytes("test".toByteArray())
        val torrent = "d4:infod6:lengthi4e4:name8:test.bin12:piece lengthi16384e6:pieces20:".toByteArray() +
            MessageDigest.getInstance("SHA-1").digest("test".toByteArray()) + "ee".toByteArray()
        val engine = createEngine(root)
        try {
            withTimeout(30_000) {
                assertEquals(torrentId, engine.addTorrent(torrent))
                assertEquals(4L, engine.files(torrentId).single().size)
                val stream = engine.prepareStream(torrentId, fileIndex = 0, minimumContiguousBytes = 4L)
                assertTrue(stream.url.startsWith("http://127.0.0.1:"))
                val full = URL(stream.url).openConnection() as HttpURLConnection
                try {
                    full.connectTimeout = 5_000
                    full.readTimeout = 10_000
                    assertEquals(200, full.responseCode)
                    assertArrayEquals("test".toByteArray(), full.inputStream.use { it.readBytes() })
                } finally { full.disconnect() }
                val range = URL(stream.url).openConnection() as HttpURLConnection
                try {
                    range.connectTimeout = 5_000
                    range.readTimeout = 10_000
                    range.setRequestProperty("Range", "bytes=1-2")
                    assertEquals(206, range.responseCode)
                    assertEquals("bytes 1-2/4", range.getHeaderField("Content-Range"))
                    assertArrayEquals("es".toByteArray(), range.inputStream.use { it.readBytes() })
                } finally { range.disconnect() }
                engine.stopStream(stream.id)
                engine.removeTorrent(torrentId)
            }
        } finally {
            engine.close()
            root.deleteRecursively()
        }
    }

    private fun newRoot(name: String) = File(
        InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir,
        "source-engine-$name",
    ).apply { deleteRecursively(); mkdirs() }

    private fun createEngine(root: File) = NuvioEngine.create(NuvioEngineConfig(
        dataDirectory = File(root, "data"),
        cacheDirectory = File(root, "cache"),
        memoryCacheCapacityBytes = 2L * 1024 * 1024,
        diskCacheCapacityBytes = 64L * 1024 * 1024,
        uploadMode = NuvioUploadMode.Disabled,
        streamInactivityTimeoutMilliseconds = 0,
        warmTorrentTimeoutMilliseconds = 0,
    ))
}
