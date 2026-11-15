package com.kovelyx.app.core.ui

import coil3.ImageLoader
import coil3.memory.MemoryCache

// Coil retains decoded (uncompressed) poster bitmaps off-heap on desktop.
// Previously 256 MiB of posters could coexist with Skia and video buffers.
// Bound the *image* cache to 128 MiB; never cap the whole JVM or player.
internal const val DesktopImageMemoryCacheBytes = 128L * 1024 * 1024

internal actual val platformProvidesImageLoader: Boolean = false

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder {
    return memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(DesktopImageMemoryCacheBytes)
            .build()
    }.components {
        add(SkiaGifDecoder.Factory())
    }
}
