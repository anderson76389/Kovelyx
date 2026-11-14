package com.kovelyx.tv.data.local

import java.io.File
import java.io.RandomAccessFile

/** Read only the OpenType 'name' table; never execute or install imported font code. */
internal fun sfntFamilyName(file: File): String? = runCatching {
    RandomAccessFile(file, "r").use { input ->
        if (input.length() < 12) return@use null
        input.seek(4)
        val tableCount = input.readUnsignedShort()
        if (tableCount !in 1..128) return@use null
        var nameOffset = -1L
        for (index in 0 until tableCount) {
            input.seek(12L + index * 16L)
            val tag = input.readInt()
            input.skipBytes(4)
            val offset = input.readInt().toLong() and 0xffffffffL
            val length = input.readInt().toLong() and 0xffffffffL
            if (tag == 0x6e616d65 && offset <= input.length() &&
                length <= input.length() - offset && length >= 6) {
                nameOffset = offset
                break
            }
        }
        if (nameOffset < 0) return@use null
        input.seek(nameOffset + 2)
        val count = input.readUnsignedShort()
        val storageOffset = input.readUnsignedShort()
        if (count !in 1..1024) return@use null
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for (index in 0 until count) {
            val recordAt = nameOffset + 6 + index * 12L
            if (recordAt + 12 > input.length()) break
            input.seek(recordAt)
            val platform = input.readUnsignedShort()
            input.readUnsignedShort() // encoding
            input.readUnsignedShort() // locale
            val nameId = input.readUnsignedShort()
            val length = input.readUnsignedShort()
            val offset = input.readUnsignedShort()
            if (nameId != 1 && nameId != 16 || platform != 3 && platform != 0 ||
                length !in 2..512 || length % 2 != 0) continue
            val from = nameOffset + storageOffset + offset
            if (from < 0 || from + length > input.length()) continue
            input.seek(from)
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val family = String(bytes, Charsets.UTF_16BE).trim()
            if (family.isBlank() || family.length > 100 || family.any { it.isISOControl() }) continue
            val rank = (if (nameId == 16) 0 else 2) + (if (platform == 3) 0 else 1)
            if (rank < bestRank) {
                best = family
                bestRank = rank
            }
        }
        best
    }
}.getOrNull()
