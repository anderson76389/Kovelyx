package com.kovelyx.app.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopImageCacheBudgetTest {
    @Test fun decodedPosterCacheUsesLowerDesktopBudget() {
        val formerBudget = 128L * 1024 * 1024
        assertEquals(96L * 1024 * 1024, DesktopImageMemoryCacheBytes)
        assertEquals(32L * 1024 * 1024, formerBudget - DesktopImageMemoryCacheBytes)
        assertTrue(DesktopImageMemoryCacheBytes >= 96L * 1024 * 1024)
    }
}
