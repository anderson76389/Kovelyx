package com.nuvio.app.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeviceLinkAuthState {
    data object Idle : DeviceLinkAuthState
    data object Starting : DeviceLinkAuthState
    data class Waiting(
        val code: String,
        val verificationUrl: String,
        val isCompleting: Boolean = false,
    ) : DeviceLinkAuthState
    data class Failed(val reason: DeviceLinkAuthFailure) : DeviceLinkAuthState
}
enum class DeviceLinkAuthFailure { Start, Expired, Complete }

/** The Cloudflare email/password account flow replaces obsolete device SQL RPCs.
 * Device-code sign-in will only be enabled after a real Worker implementation. */
object DeviceLinkAuthRepository {
    private val _state = MutableStateFlow<DeviceLinkAuthState>(DeviceLinkAuthState.Idle)
    val state: StateFlow<DeviceLinkAuthState> = _state.asStateFlow()
    fun start() { _state.value = DeviceLinkAuthState.Failed(DeviceLinkAuthFailure.Start) }
    fun cancel() { _state.value = DeviceLinkAuthState.Idle }
}
internal fun formatDeviceLinkCode(value: String): String {
    val normalized = value.uppercase().filter { it.isLetterOrDigit() }.take(6)
    return if (normalized.length <= 3) normalized else "${normalized.take(3)}-${normalized.drop(3)}"
}
