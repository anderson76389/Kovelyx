package com.kovelyx.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class DisplayRefreshPolicyTest {
    @Test fun playbackVoteIsTemporaryAndCannotEscapeUserBounds() {
        val state = DisplayRefreshController()
        var writes = 0
        state.initialize(60, 144, 60) { _, _ -> writes++ }
        state.setPlaybackMode(mode60)
        assertEquals(mode60, displayRefreshCandidates(modes, mode60, 60, 144, state.state.value.playbackMode).first())
        assertEquals(mode144, displayRefreshCandidates(modes, mode60, 90, 144, state.state.value.playbackMode).first())
        state.setPlaybackMode(null)
        assertEquals(mode144, displayRefreshCandidates(modes, mode60, 60, 144, state.state.value.playbackMode).first())
        assertEquals(0, writes)
    }

    @Test fun pickerAdjustsOtherBoundAtomicallyAndRejectsInventedModes() {
        val state = DisplayRefreshController()
        val writes = mutableListOf<Pair<Int, Int>>()
        state.initialize(60, 60, 60) { minimum, maximum -> writes += minimum to maximum }
        state.displayChanged(setOf(24, 60, 120, 144), 60f)
        state.chooseMinimum(144)
        assertEquals(144 to 144, writes.last())
        state.chooseMaximum(24)
        assertEquals(24 to 24, writes.last())
        assertFailsWith<IllegalArgumentException> { state.chooseMaximum(90) }
        assertEquals(2, writes.size)
    }

    @Test fun windowsModeChangesRequireSeparateConsentAndRemainOffByDefault() {
        val state = DisplayRefreshController()
        var persisted = false
        var writes = 0
        state.initialize(60, 120, 60) { _, _ -> writes++ }
        state.configureSystemModeConsent(
            required = true,
            granted = false,
            persist = { persisted = it },
        )
        assertTrue(state.state.value.requiresSystemModeConsent)
        assertTrue(!state.state.value.systemModeConsentGranted)
        // Existing refresh-range preferences alone cannot grant OS-wide mode changes.
        assertEquals(0, writes)
        assertTrue(!persisted)
        state.setSystemModeConsent(true)
        assertTrue(state.state.value.systemModeConsentGranted)
        assertTrue(persisted)
        state.setSystemModeConsent(false)
        assertTrue(!state.state.value.systemModeConsentGranted)
        assertTrue(!persisted)
    }

    @Test fun normalAndroidRefreshSettingsDoNotRequireWindowsConsent() {
        val state = DisplayRefreshController()
        state.initialize(60, 120, 60) { _, _ -> }
        assertTrue(!state.state.value.requiresSystemModeConsent)
        assertTrue(state.state.value.systemModeConsentGranted)
        state.setSystemModeConsent(false)
        assertTrue(state.state.value.systemModeConsentGranted)
    }

    private val mode60 = DisplayModeSpec(1, 3840, 2160, 59.94f)
    private val mode90 = mode60.copy(id = 2, hertz = 90f)
    private val mode120 = mode60.copy(id = 3, hertz = 119.88f)
    private val mode144 = mode60.copy(id = 4, hertz = 143.856f)
    private val modes = listOf(mode60, mode90, mode120, mode144)

    @Test fun preservesResolutionEvenWhenHigherRatesExistAtLowerResolution() {
        val choices = displayRefreshCandidates(listOf(mode60, DisplayModeSpec(5, 1920, 1080, 144f)), mode60, 60, 144)
        assertEquals(listOf(mode60), choices)
    }
    @Test fun capsAt144AndIncludesIntermediateSupportedRates() {
        val choices = displayRefreshCandidates(modes + mode60.copy(id = 5, hertz = 165f), mode60, 60, 144)
        assertEquals(listOf(mode144, mode120, mode90, mode60), choices)
    }
    @Test fun neverFallsBelowMinimumOrAboveMaximum() {
        assertEquals(listOf(mode120, mode90), displayRefreshCandidates(modes, mode60, 90, 120))
        assertTrue(displayRefreshCandidates(listOf(mode60, mode144), mode60, 90, 120).isEmpty())
    }
    @Test fun fixed144RangeOnlyRequests144() {
        assertEquals(listOf(mode144), displayRefreshCandidates(modes, mode60, 144, 144))
    }
    @Test fun supportsFractionalAndLowerTvRatesWithoutInventingModes() {
        assertEquals(24, nominalRefreshRate(23.976f))
        assertEquals(60, nominalRefreshRate(59.94f))
        assertEquals(120, nominalRefreshRate(119.88f))
        assertEquals(144, nominalRefreshRate(143.856f))
        assertNull(nominalRefreshRate(165f))
        assertNull(nominalRefreshRate(Float.NaN))
        assertNull(nominalRefreshRate(Float.POSITIVE_INFINITY))
        val mode50 = mode60.copy(id = 5, hertz = 50f)
        assertEquals(listOf(mode50), displayRefreshCandidates(listOf(mode50), mode50, 24, 60))
    }
    @Test fun rejectsInvertedOrOutOfBoundsRangesBeforeSaving() {
        val state = DisplayRefreshController()
        var writes = 0
        state.initialize(60, 120, 60) { _, _ -> writes++ }
        for ((minimum, maximum) in listOf(0 to 120, 60 to 145, 120 to 60, -1 to 144)) {
            assertFailsWith<IllegalArgumentException> { state.selectRange(minimum, maximum) }
        }
        assertFailsWith<IllegalArgumentException> { state.selectMinimum(144) }
        assertFailsWith<IllegalArgumentException> { state.selectMaximum(30) }
        assertEquals(0, writes)
        assertEquals(60, state.state.value.minimum)
        assertEquals(120, state.state.value.maximum)
    }
    @Test fun migratesPrevious120ChoiceWithIts60Fallback() {
        assertEquals(60 to 120, restoreDisplayRefreshRange(null, null, 120))
        assertEquals(60 to 60, restoreDisplayRefreshRange(null, null, 60))
        assertEquals(90 to 144, restoreDisplayRefreshRange(90, 144, 60))
    }
    @Test fun corruptStorageDoesNotRestoreAnInvalidRange() {
        assertEquals(60 to 120, restoreDisplayRefreshRange(144, 60, 120))
        assertEquals(60 to 60, restoreDisplayRefreshRange(0, 165, 999))
    }
    @Test fun boundsPersistTogetherAndRemainIndependentAcrossDevices() {
        val pc = DisplayRefreshController(); val tv = DisplayRefreshController()
        val writes = mutableListOf<Pair<Int, Int>>()
        pc.initialize(60, 144, 60) { minimum, maximum -> writes += minimum to maximum }
        tv.initialize(60, 60, 60) { _, _ -> }
        pc.selectMinimum(90); pc.selectMaximum(120)
        assertEquals(listOf(90 to 144, 90 to 120), writes)
        assertEquals(60, tv.state.value.minimum); assertEquals(60, tv.state.value.maximum)
        val restarted = DisplayRefreshController()
        restarted.initialize(writes.last().first, writes.last().second, 60) { _, _ -> }
        assertEquals(90, restarted.state.value.minimum); assertEquals(120, restarted.state.value.maximum)
    }
    @Test fun unchangedRangeDoesNotRewritePreferences() {
        val state = DisplayRefreshController(); var writes = 0
        state.initialize(60, 144, 60) { _, _ -> writes++ }
        state.selectRange(60, 144)
        assertEquals(0, writes)
    }
    @Test fun reportsActualRangeComplianceWithoutClaimingUnsupportedRates() {
        val state = DisplayRefreshController()
        state.initialize(90, 144, 60) { _, _ -> }
        state.displayChanged(setOf(60, 90, 144, 165), 60f)
        assertEquals(setOf(60, 90, 144), state.state.value.available)
        assertTrue(!state.state.value.appliedInRange)
        state.displayChanged(setOf(60, 90, 144), 165f)
        assertEquals(165, state.state.value.actual)
        assertTrue(!state.state.value.appliedInRange)
        state.displayChanged(setOf(60, 90, 144), 143.856f)
        assertTrue(state.state.value.appliedInRange)
    }
}
