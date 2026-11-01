package com.kovelyx.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val hertz: Float)

fun nominalRefreshRate(hertz: Float): Int? = listOf(60, 120).firstOrNull { abs(hertz - it) < 0.6f }

/** Never trade resolution for refresh rate, or pick a 90/144 Hz mode. */
fun selectDisplayRefreshMode(modes: List<DisplayModeSpec>, current: DisplayModeSpec, requested: Int): DisplayModeSpec? {
    require(requested == 60 || requested == 120)
    val sameSize = modes.filter { it.width == current.width && it.height == current.height }
    return listOf(requested, 60).distinct().firstNotNullOfOrNull { target ->
        sameSize.filter { nominalRefreshRate(it.hertz) == target }
            .minByOrNull { abs(it.hertz - target) }
    }
}

data class DisplayRefreshState(
    val enabled: Boolean = false,
    val requested: Int = 60,
    val available: Set<Int> = emptySet(),
    val actual: Int? = null,
)

/** Device setting: do not copy a TV's hardware capabilities into another device's account. */
object DisplayRefreshSettings {
    private val mutable = MutableStateFlow(DisplayRefreshState())
    val state = mutable.asStateFlow()
    private var save: (Int) -> Unit = {}
    fun initialize(stored: Int, persist: (Int) -> Unit) {
        save = persist
        mutable.value = mutable.value.copy(enabled = true, requested = if (stored == 120) 120 else 60)
    }
    fun select(hertz: Int) {
        require(hertz == 60 || hertz == 120)
        if (hertz == mutable.value.requested) return
        save(hertz)
        mutable.value = mutable.value.copy(requested = hertz)
    }
    fun displayChanged(available: Set<Int>, actual: Float) {
        mutable.value = mutable.value.copy(available = available.intersect(setOf(60, 120)), actual = nominalRefreshRate(actual))
    }
}
