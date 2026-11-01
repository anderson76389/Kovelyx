package com.kovelyx.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class DisplayRefreshPolicyTest {
    private val ultraHd60 = DisplayModeSpec(1, 3840, 2160, 59.94f)
    @Test fun preservesResolutionWhenOnlyLowerResolutionSupports120Hz() {
        assertEquals(ultraHd60, selectDisplayRefreshMode(listOf(ultraHd60, DisplayModeSpec(2, 1920, 1080, 120f)), ultraHd60, 120))
    }
    @Test fun neverSelects90Or144HzInsteadOfTheRequestedRate() {
        val requested = DisplayModeSpec(2, 3840, 2160, 119.88f)
        val fast = DisplayModeSpec(3, 3840, 2160, 144f)
        assertEquals(requested, selectDisplayRefreshMode(listOf(ultraHd60, requested, fast), fast, 120))
        assertEquals(ultraHd60, selectDisplayRefreshMode(listOf(ultraHd60, requested, fast), fast, 60))
    }
    @Test fun unsupportedDisplayIsNotReportedAs60Or120Hz() {
        val current = DisplayModeSpec(1, 1920, 1080, 50f)
        assertNull(selectDisplayRefreshMode(listOf(current, current.copy(id=2, hertz=90f)), current, 120))
    }
    @Test fun rejectsValuesOutsideTheTwoChoices() {
        assertFailsWith<IllegalArgumentException> { selectDisplayRefreshMode(listOf(ultraHd60), ultraHd60, 90) }
        assertFailsWith<IllegalArgumentException> { DisplayRefreshSettings.select(144) }
    }
}
