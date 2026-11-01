package com.kovelyx.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

const val MAX_DISPLAY_HERTZ = 144

data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val hertz: Float)

fun nominalRefreshRate(hertz: Float): Int? {
    if (!hertz.isFinite() || hertz <= 0f) return null
    val rounded = hertz.roundToInt()
    return rounded.takeIf { it in 1..MAX_DISPLAY_HERTZ && abs(hertz - it) < 0.6f }
}

fun validateDisplayRefreshRange(minimum: Int, maximum: Int) {
    require(minimum in 1..MAX_DISPLAY_HERTZ && maximum in minimum..MAX_DISPLAY_HERTZ) {
        "La fréquence minimale doit être inférieure ou égale à la maximale, jusqu’à 144 Hz."
    }
}

/** Preserve resolution and request the fastest supported mode within BOTH bounds. */
fun displayRefreshCandidates(modes: List<DisplayModeSpec>, current: DisplayModeSpec, minimum: Int, maximum: Int): List<DisplayModeSpec> {
    validateDisplayRefreshRange(minimum, maximum)
    return modes.filter { mode -> mode.width == current.width && mode.height == current.height &&
        nominalRefreshRate(mode.hertz)?.let { it in minimum..maximum } == true }
        .sortedByDescending { it.hertz }
}

fun restoreDisplayRefreshRange(minimum: Int?, maximum: Int?, legacy: Int): Pair<Int, Int> {
    if (minimum != null && maximum != null && minimum in 1..MAX_DISPLAY_HERTZ && maximum in minimum..MAX_DISPLAY_HERTZ)
        return minimum to maximum
    // The former 120 Hz choice allowed a 60 Hz fallback. Keep that behaviour.
    val previous = if (legacy == 120) 120 else 60
    return 60 to previous
}

data class DisplayRefreshState(
    val enabled: Boolean = false,
    val minimum: Int = 60,
    val maximum: Int = 60,
    val available: Set<Int> = emptySet(),
    val actual: Int? = null,
) {
    val appliedInRange: Boolean get() = actual?.let { it in minimum..maximum } == true
}

/** Reusable state holder; persistence belongs to the device, never the cloud account. */
open class DisplayRefreshController {
    private val mutable = MutableStateFlow(DisplayRefreshState())
    val state = mutable.asStateFlow()
    private var save: (Int, Int) -> Unit = { _, _ -> }
    fun initialize(minimum: Int?, maximum: Int?, legacy: Int, persist: (Int, Int) -> Unit) {
        val restored = restoreDisplayRefreshRange(minimum, maximum, legacy)
        save = persist
        mutable.value = mutable.value.copy(enabled = true, minimum = restored.first, maximum = restored.second)
    }
    fun selectRange(minimum: Int, maximum: Int) {
        validateDisplayRefreshRange(minimum, maximum)
        if (minimum == mutable.value.minimum && maximum == mutable.value.maximum) return
        save(minimum, maximum)
        mutable.value = mutable.value.copy(minimum = minimum, maximum = maximum)
    }
    fun selectMinimum(hertz: Int) = selectRange(hertz, mutable.value.maximum)
    fun selectMaximum(hertz: Int) = selectRange(mutable.value.minimum, hertz)
    fun displayChanged(available: Set<Int>, actual: Float) {
        mutable.value = mutable.value.copy(available = available.filter { it in 1..MAX_DISPLAY_HERTZ }.toSet(), actual = actual.takeIf { it.isFinite() && it > 0f }?.roundToInt())
    }
}

object DisplayRefreshSettings : DisplayRefreshController()
