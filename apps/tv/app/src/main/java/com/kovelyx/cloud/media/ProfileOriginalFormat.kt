package com.kovelyx.cloud.media

/** Supported originals are transferred unchanged, including animation and metadata. */
fun profileOriginalMime(header: ByteArray): String? {
    fun at(offset: Int, vararg bytes: Int) = header.size >= offset + bytes.size &&
        bytes.indices.all { (header[offset + it].toInt() and 255) == bytes[it] }
    return when {
        isProfileGif(header) -> "image/gif"
        at(0, 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10) -> "image/png"
        at(0, 0xff, 0xd8, 0xff) -> "image/jpeg"
        at(0, 82, 73, 70, 70) && at(8, 87, 69, 66, 80) -> "image/webp"
        else -> null
    }
}
