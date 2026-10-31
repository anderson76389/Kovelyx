package com.kovelyx.cloud.media

import com.nuvio.app.core.storage.DesktopStorage
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopProfileMediaFilesTest {
    @Test fun `animated GIF bytes remain intact and downloaded bytes are kept in a private file`() = runBlocking {
        val folder = DesktopStorage.rootDir.resolve("profile-media")
        Files.createDirectories(folder)
        val source = folder.resolve("test-${UUID.randomUUID()}.gif")
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        try {
            ImageIO.createImageOutputStream(source.toFile()).use { output ->
                writer.output = output
                writer.prepareWriteSequence(null)
                repeat(2) { index ->
                    val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
                    image.setRGB(0, 0, if (index == 0) 0xff0000 else 0x00ff00)
                    writer.writeToSequence(IIOImage(image, null, null), null)
                }
                writer.endWriteSequence()
            }
            val files = DesktopProfileMediaFiles()
            val image = files.encodeLocal(source.toUri().toString(), background = true)
            assertEquals("image/gif", image.mime)
            assertContentEquals(Files.readAllBytes(source), image.bytes)
            val version = files.sha256(image.bytes)
            val downloaded = files.saveDownloaded("test-${UUID.randomUUID()}", version, image)
            val cached = Path.of(java.net.URI(downloaded))
            try {
                assertTrue(cached.startsWith(DesktopStorage.rootDir))
                assertContentEquals(image.bytes, Files.readAllBytes(cached))
                ImageIO.createImageInputStream(cached.toFile()).use { stream ->
                    val reader = ImageIO.getImageReaders(stream).next()
                    try { reader.input = stream; assertEquals(2, reader.getNumImages(true)) }
                    finally { reader.dispose() }
                }
            } finally { Files.deleteIfExists(cached) }
        } finally { writer.dispose(); Files.deleteIfExists(source) }
    }
}
