package com.nuvio.tv.core.auth

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.auth.diagnostics.AuthDiagnosticsSession
import com.nuvio.tv.data.remote.cloud.DeviceLoginStartResult
import com.nuvio.tv.data.remote.cloud.TvLoginPollResult
import com.nuvio.tv.data.remote.cloud.TvLoginStartResult
import com.nuvio.tv.domain.model.AuthState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Kovelyx TV account identity is exclusively Cloudflare Workers / Better Auth.
 * Never construct an old SDK client or send credentials to a foreign backend.
 */
@Singleton
class AuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        if (CloudflareTvAccountClient.enabled) {
            CloudflareTvAccountClient.initialize(context)
            scope.launch {
                val restored = runCatching { CloudflareTvAccountClient.restoreSession(context) }
                    .onFailure { Log.w("AuthManager", "La session Kovelyx Cloudflare reste indisponible", it) }
                    .getOrNull()
                if (_authState.value == AuthState.Loading)
                    _authState.value = restored?.let { AuthState.FullAccount(it.id, it.email) } ?: AuthState.SignedOut
            }
        } else {
            _authState.value = AuthState.SignedOut
        }
    }

    val isAuthenticated: Boolean get() = _authState.value is AuthState.FullAccount
    val currentUserId: String? get() = (_authState.value as? AuthState.FullAccount)?.userId

    suspend fun getEffectiveUserId(fallbackToOwnIdOnFailure: Boolean = true): String? = currentUserId

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = runCatching {
        check(CloudflareTvAccountClient.enabled) { "Kovelyx Cloudflare non configuré" }
        CloudflareTvAccountClient.signUp(email, password)
        Unit
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        check(CloudflareTvAccountClient.enabled) { "Kovelyx Cloudflare non configuré" }
        val account = CloudflareTvAccountClient.signIn(email, password)
        _authState.value = AuthState.FullAccount(account.id, account.email)
    }

    suspend fun signOut(explicit: Boolean = true) {
        val previousToken = CloudflareTvAccountClient.account?.bearerToken
        CloudflareTvAccountClient.clearLocalSession()
        _authState.value = AuthState.SignedOut
        if (!previousToken.isNullOrBlank()) scope.launch {
            try {
                CloudflareTvAccountClient.revokePreviousSession(previousToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AuthManager", "Compte déconnecté localement, révocation distante différée", error)
            }
        }
    }

    suspend fun prepareForServerSwitch(): Result<Unit> = runCatching {
        CloudflareTvAccountClient.clearLocalSession()
        _authState.value = AuthState.SignedOut
    }

    fun clearEffectiveUserIdCache() = Unit

    suspend fun refreshSessionIfJwtExpired(error: Throwable): Boolean = false

    private fun pairingUnavailable(): UnsupportedOperationException =
        UnsupportedOperationException("La liaison TV par QR/code n'est pas disponible sur le backend Kovelyx.")

    suspend fun startDeviceLoginSession(
        deviceNonce: String,
        deviceName: String?,
        deviceType: String,
        redirectBaseUrl: String,
        legacyRedirectBaseUrl: String,
        traceId: Long? = null,
        diagnostics: AuthDiagnosticsSession? = null
    ): Result<DeviceLoginStartResult> = Result.failure(pairingUnavailable())

    suspend fun startTvLoginSession(
        deviceNonce: String,
        deviceName: String?,
        redirectBaseUrl: String,
        traceId: Long? = null,
        diagnostics: AuthDiagnosticsSession? = null
    ): Result<TvLoginStartResult> = Result.failure(pairingUnavailable())

    suspend fun pollTvLoginSession(
        code: String,
        deviceNonce: String,
        traceId: Long? = null,
        attempt: Int? = null,
        diagnostics: AuthDiagnosticsSession? = null
    ): Result<TvLoginPollResult> = Result.failure(pairingUnavailable())

    suspend fun exchangeTvLoginSession(
        code: String,
        deviceNonce: String,
        traceId: Long? = null,
        diagnostics: AuthDiagnosticsSession? = null
    ): Result<Unit> = Result.failure(pairingUnavailable())
}
