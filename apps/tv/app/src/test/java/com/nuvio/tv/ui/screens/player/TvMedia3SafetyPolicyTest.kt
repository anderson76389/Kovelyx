package com.nuvio.tv.ui.screens.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TvMedia3SafetyPolicyTest {
    @Test fun localhostHttpUsesSafeRoute() {
        assertTrue(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "127.0.0.1"))
        assertTrue(TvMedia3SafetyPolicy.useSafeLoopbackHttp("https", "LOCALHOST"))
        assertTrue(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "[::1]"))
        assertTrue(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "127.0.0.2"))
        assertTrue(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "127.5.24.250"))
        assertTrue(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "[0:0:0:0:0:0:0:1]"))
        assertFalse(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "127.0.0.999"))
        assertFalse(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "128.0.0.1"))
        assertFalse(TvMedia3SafetyPolicy.useSafeLoopbackHttp("http", "example.com"))
        assertFalse(TvMedia3SafetyPolicy.useSafeLoopbackHttp("file", "localhost"))
        assertFalse(TvMedia3SafetyPolicy.useSafeLoopbackHttp(null, null))
    }

    @Test fun nativeAllocatorRemainsDisabledUntilPatched() {
        assertFalse(TvMedia3SafetyPolicy.allowNativeAllocation)
    }

    @Test fun localhostErrorIsNotReplacedWithInventedHttp400() {
        val http = mockk<DataSource>(relaxed = true)
        val normal = mockk<DataSource>(relaxed = true)
        val uri = mockk<Uri>()
        every { uri.scheme } returns "http"
        every { uri.host } returns "127.0.0.1"
        val spec = DataSpec.Builder().setUri(uri).build()
        every { http.open(any()) } throws IOException("HTTP 404")
        val source = SafeLoopbackDataSource(http, normal)

        val thrown = assertThrows(IOException::class.java) { source.open(spec) }
        assertEquals("HTTP 404", thrown.message)
        verify(exactly = 1) { http.open(any()) }
        verify(exactly = 0) { normal.open(any()) }
    }

    @Test fun nonLoopbackKeepsStandardDataSource() {
        val http = mockk<DataSource>(relaxed = true)
        val normal = mockk<DataSource>(relaxed = true)
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.host } returns null
        val spec = DataSpec.Builder().setUri(uri).build()
        every { normal.open(any()) } returns 21L
        val source = SafeLoopbackDataSource(http, normal)
        try {
            assertEquals(21L, source.open(spec))
            verify(exactly = 0) { http.open(any()) }
        } finally { source.close() }
    }
}
