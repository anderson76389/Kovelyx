package com.kovelyx.tv.data.local

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SfntFontFamilyTest {
    @Test fun readsOpenTypeFamilyWithoutLoadingFont() {
        val family = "Police FR"
        val utf16 = family.toByteArray(Charsets.UTF_16BE)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x00010000) // TrueType SFNT
            out.writeShort(1) // one table
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            out.writeInt(0x6e616d65) // "name"
            out.writeInt(0)
            out.writeInt(28)
            out.writeInt(6 + 12 + utf16.size)
            out.writeShort(0)
            out.writeShort(1)
            out.writeShort(18)
            out.writeShort(3) // Windows Unicode
            out.writeShort(1)
            out.writeShort(1033)
            out.writeShort(1) // family
            out.writeShort(utf16.size)
            out.writeShort(0)
            out.write(utf16)
        }
        val temporary = File.createTempFile("kovelyx-subtitle-font-", ".ttf")
        try {
            temporary.writeBytes(bytes.toByteArray())
            assertEquals(family, sfntFamilyName(temporary))
        } finally {
            temporary.delete()
        }
    }

    @Test fun malformedFontNeverReturnsArbitraryFamily() {
        val temporary = File.createTempFile("kovelyx-subtitle-font-malformed-", ".ttf")
        try {
            temporary.writeBytes(byteArrayOf(0, 0, 0, 0))
            assertNull(sfntFamilyName(temporary))
        } finally {
            temporary.delete()
        }
    }
}
