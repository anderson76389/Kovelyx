package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Describes the configured player path, not a claim about the driver's internal AI work. */
object NvidiaRtxRuntimeStatus {
    private val mutable = MutableStateFlow(-1)
    val state = mutable.asStateFlow()
    fun update(value: Int) { mutable.value = value.takeIf { it in 0..2 } ?: -1 }
}
