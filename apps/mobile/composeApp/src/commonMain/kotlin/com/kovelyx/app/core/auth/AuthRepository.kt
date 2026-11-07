package com.kovelyx.app.core.auth

import co.touchlab.kermit.Logger
import com.kovelyx.app.core.cloud.CloudflareAccountClient
import com.kovelyx.app.core.storage.LocalAccountDataCleaner
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Account sessions are owned exclusively by Kovelyx Workers/Better Auth.
 * No retired authentication SDK, tokens, RPCs or upstream sessions.
 */
object AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AuthRepository")
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var initialized = false
    private var sessionStatusJob: Job? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        val anonId = AuthStorage.loadAnonymousUserId()
        if (anonId != null) {
            _state.value = AuthState.Authenticated(
                userId = anonId, email = null, isAnonymous = true,
            )
            return
        }
        if (!CloudflareAccountClient.enabled) {
            _state.value = AuthState.Unauthenticated
            return
        }
        _state.value = AuthState.Loading
        sessionStatusJob = scope.launch {
            val restored = runCatching { CloudflareAccountClient.restoreSession() }
                .onFailure { log.w(it) { "Cloudflare session restore failed" } }
                .getOrNull()
            if (_state.value == AuthState.Loading) {
                _state.value = if (restored == null) AuthState.Unauthenticated else AuthState.Authenticated(
                    userId = restored.userId, email = restored.email, isAnonymous = false,
                )
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun signInAnonymously() {
        sessionStatusJob?.cancel()
        sessionStatusJob = null
        _error.value = null
        val userId = Uuid.random().toString()
        AuthStorage.saveAnonymousUserId(userId)
        _state.value = AuthState.Authenticated(
            userId = userId, email = null, isAnonymous = true,
        )
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        check(CloudflareAccountClient.enabled) { "Serveur Cloudflare Kovelyx non configuré" }
        CloudflareAccountClient.signUp(email, password)
        Unit
    }.onFailure { error ->
        log.e(error) { "Kovelyx account sign-up failed" }
        _error.value = error.message ?: getString(Res.string.auth_sign_up_failed)
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        check(CloudflareAccountClient.enabled) { "Serveur Cloudflare Kovelyx non configuré" }
        val account = CloudflareAccountClient.signIn(email, password)
        sessionStatusJob?.cancel()
        AuthStorage.clearAnonymousUserId()
        _state.value = AuthState.Authenticated(
            userId = account.userId, email = account.email, isAnonymous = false,
        )
        Unit
    }.onFailure { error ->
        log.e(error) { "Kovelyx account sign-in failed" }
        _error.value = error.message ?: getString(Res.string.auth_sign_in_failed)
    }

    suspend fun signOut(): Result<Unit> {
        _error.value = null
        sessionStatusJob?.cancel()
        sessionStatusJob = null
        if (CloudflareAccountClient.enabled) {
            // Always discard credentials locally before any network request.
            val token = CloudflareAccountClient.currentSession?.bearerToken
            CloudflareAccountClient.clearLocalSession()
            AuthStorage.clearAnonymousUserId()
            _state.value = AuthState.Unauthenticated
            if (!token.isNullOrBlank()) scope.launch {
                try {
                    CloudflareAccountClient.revokePreviousSession(token)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.w(error) { "Cloudflare sign-out completed locally; remote revocation deferred" }
                }
            }
            return Result.success(Unit)
        }
        return runCatching {
            AuthStorage.clearAnonymousUserId()
            LocalAccountDataCleaner.wipe()
            _state.value = AuthState.Unauthenticated
        }.onFailure { error ->
            _state.value = AuthState.Unauthenticated
            _error.value = error.message ?: getString(Res.string.auth_sign_out_failed)
        }
    }

    suspend fun prepareForServerSwitch(): Result<Unit> = runCatching {
        sessionStatusJob?.cancel()
        sessionStatusJob = null
        if (CloudflareAccountClient.enabled) CloudflareAccountClient.clearLocalSession()
        AuthStorage.clearAnonymousUserId()
        _state.value = AuthState.Unauthenticated
        _error.value = null
    }

    fun reinitialize() {
        sessionStatusJob?.cancel()
        sessionStatusJob = null
        initialized = false
        _state.value = AuthState.Loading
        initialize()
    }

    /** Legacy session errors cannot invalidate an independent cloud account. */
    suspend fun signOutIfSessionInvalid(error: Throwable, source: String): Boolean = false

    /** Never pretend a cloud account is deleted until a dedicated API confirms it. */
    suspend fun deleteAccount(): Result<Unit> = Result.failure<Unit>(
        UnsupportedOperationException("Suppression du compte Cloudflare indisponible : aucune donnée supprimée.")
    ).also { outcome -> _error.value = outcome.exceptionOrNull()?.message }

    fun clearError() {
        _error.value = null
    }
}
