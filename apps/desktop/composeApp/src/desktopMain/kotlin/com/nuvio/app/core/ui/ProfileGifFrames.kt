package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.kovelyx.cloud.media.MAX_PROFILE_MEDIA_BYTES
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/** One codec and one decode buffer per visible animation; never preload all frames. */
internal class ProfileGifFrames(path: Path, maxSide: Int = 1024) : AutoCloseable {
    private val codec: Codec
    private val buffer: Bitmap
    val frameCount: Int
    private val width: Int
    private val height: Int

    init {
        require(Files.size(path) in 1..MAX_PROFILE_MEDIA_BYTES.toLong())
        codec = Data.makeFromBytes(Files.readAllBytes(path)).use { Codec.makeFromData(it) }
        try {
            require(codec.width > 0 && codec.height > 0 && codec.width.toLong() * codec.height <= 32_000_000L)
            frameCount = codec.frameCount
            require(frameCount > 0)
            val scale = minOf(1.0, maxSide.toDouble() / maxOf(codec.width, codec.height))
            width = (codec.width * scale).toInt().coerceAtLeast(1)
            height = (codec.height * scale).toInt().coerceAtLeast(1)
            buffer = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(codec.width, codec.height)) }
        } catch (error: Throwable) {
            codec.close()
            throw error
        }
    }

    fun durationMillis(index: Int): Long = codec.getFrameInfo(index).duration.toLong().let {
        if (it <= 0) 100L else it.coerceAtLeast(20L)
    }

    fun frame(index: Int): ImageBitmap = frameBitmap(index).asComposeImageBitmap()

    internal fun frameBitmap(index: Int): Bitmap {
        codec.readPixels(buffer, index)
        // Publish an independent frame so decoding cannot mutate a bitmap being drawn.
        val output = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(width, height)) }
        try {
            Image.makeFromBitmap(buffer).use { image ->
                Canvas(output).use { canvas ->
                    canvas.drawImageRect(image, Rect.makeWH(codec.width.toFloat(), codec.height.toFloat()),
                        Rect.makeWH(width.toFloat(), height.toFloat()), SamplingMode.LINEAR, null, true)
                }
            }
            return output
        } catch (error: Throwable) { output.close(); throw error }
    }

    override fun close() { buffer.close(); codec.close() }
}
