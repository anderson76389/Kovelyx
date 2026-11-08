package com.kovelyx.tv.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvImageCacheBudgetTest {
    private val mib = 1024L * 1024L

    @Test
    fun `two gigabyte TV box uses smaller disk and memory caches`() {
        assertEquals(128L * mib, TvImageCacheBudget.diskBytes(2048, constrainedTv = true))
        assertEquals(0.10, TvImageCacheBudget.memoryClassFraction(2048, constrainedTv = true), 0.0001)
    }

    @Test
    fun `three gigabyte TV box has bounded caches`() {
        assertEquals(160L * mib, TvImageCacheBudget.diskBytes(3072, constrainedTv = false))
        assertEquals(0.15, TvImageCacheBudget.memoryClassFraction(3072, constrainedTv = false), 0.0001)
    }

    @Test
    fun `larger box retains the established disk budget and lower RAM share`() {
        assertEquals(200L * mib, TvImageCacheBudget.diskBytes(4096, constrainedTv = false))
        assertEquals(0.20, TvImageCacheBudget.memoryClassFraction(4096, constrainedTv = false), 0.0001)
    }

    @Test
    fun `android low ram classification overrides reported physical RAM`() {
        assertEquals(128L * mib, TvImageCacheBudget.diskBytes(8192, constrainedTv = true))
        assertEquals(0.10, TvImageCacheBudget.memoryClassFraction(8192, constrainedTv = true), 0.0001)
    }

    @Test
    fun `cache budgets remain positive and bounded for unexpected memory readings`() {
        listOf(0L, 512L, 2048L, 3072L, 8192L).forEach { mb ->
            assertTrue(TvImageCacheBudget.diskBytes(mb, constrainedTv = false) in 128L * mib..200L * mib)
            assertTrue(TvImageCacheBudget.memoryClassFraction(mb, constrainedTv = false) in 0.10..0.20)
        }
    }
}
