package com.kovelyx.tv.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRevalidationBudgetTest {
    @Test
    fun cachedPostersNeverQueueMoreThanEightNetworkCalls() {
        val budget = TvRevalidationBudget()
        repeat(8) { assertTrue(budget.tryStart("https://poster.invalid/$it", 0L)) }
        assertEquals(8, budget.inFlightCountForTest())
        assertFalse(budget.tryStart("https://poster.invalid/ninth", 0L))
        assertFalse(budget.tryStart("https://poster.invalid/0", 0L))
        budget.finish("https://poster.invalid/0", 1L)
        assertTrue(budget.tryStart("https://poster.invalid/ninth", 1L))
        assertEquals(8, budget.inFlightCountForTest())
    }

    @Test
    fun tenThousandDifferentPostersUseAtMost256HistoryEntries() {
        val budget = TvRevalidationBudget()
        repeat(10_000) {
            val url = "https://poster.invalid/image/$it"
            assertTrue(budget.tryStart(url, 1_000L))
            budget.finish(url, 1_001L)
        }
        assertEquals(256, budget.rememberedCountForTest())
        assertEquals(0, budget.inFlightCountForTest())
    }

    @Test
    fun repeatedPosterRefreshRespectsCooldownAndCanRefreshLater() {
        val budget = TvRevalidationBudget()
        val url = "https://poster.invalid/one"
        assertTrue(budget.tryStart(url, 500L))
        budget.finish(url, 501L)
        assertFalse(budget.tryStart(url, 502L))
        assertFalse(budget.tryStart(url, 600_501L))
        assertTrue(budget.tryStart(url, 600_502L))
        budget.finish(url, 600_503L)
    }

    @Test
    fun finishingAnUnregisteredJobDoesNotRetainItsUrl() {
        val budget = TvRevalidationBudget()
        budget.finish("https://poster.invalid/absent", 1L)
        assertEquals(0, budget.rememberedCountForTest())
    }
}
