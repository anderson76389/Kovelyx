package com.nuvio.app.core.auth

import co.touchlab.kermit.Logger
import com.nuvio.app.core.cloud.CloudflareAccountClient
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

/**
 * Comptes Kovelyx : Cloudflare Workers + D1 / Better Auth uniquement.
 * Le mode invité reste local. Aucun SDK d'authentification tiers n'est utilisé.
 */
object AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("KovelyxAccount")

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true

        val savedGuest = AuthStorage.loadAnonymousUserId()
        if (savedGuest != null) {
            _state.value = AuthState.Authenticated(
                userId = savedGuest,
                email = null,
                isAnonymous = true,
            )
            return
        }

        if (!CloudflareAccountClient.enabled) {
            _state.value = AuthState.Unauthenticated
            return
        }

        _state.value = AuthState.Loading
        scope.launch {
            // The Worker must validate the stored token before it is trusted.
            // A manual login, logout or guest selection wins over a late reply.
            val restored = CloudflareAccountClient.restoreSession()
            if (_state.value == AuthState.Loading) {
                _state.value = if (restored == null) {
                    AuthState.Unauthenticated
                } else {
                    AuthState.Authenticated(
                        userId = restored.userId,
                        email = restored.email,
                        isAnonymous = false,
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun signInAnonymously() {
        _error.value = null
        // Invalidate a concurrent restore and isolate the local guest session.
        CloudflareAccountClient.clearLocalSession()
        val guestId = Uuid.random().toString()
        AuthStorage.saveAnonymousUserId(guestId)
        _state.value = AuthState.Authenticated(
            userId = guestId,
            email = null,
            isAnonymous = true,
        )
    }

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        check(CloudflareAccountClient.enabled) {
            "Serveur de comptes Kovelyx Cloudflare non configuré."
        }
        CloudflareAccountClient.signUp(email, password)
    }.onFailure { failure ->
        log.e(failure) { "Création du compte Kovelyx impossible" }
        _error.value = failure.message?.takeIf(String::isNotBlank)
            ?: getString(Res.string.auth_sign_up_failed)
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        _error.value = null
        check(CloudflareAccountClient.enabled) {
            "Serveur de comptes Kovelyx Cloudflare non configuré."
        }
        val account = CloudflareAccountClient.signIn(email, password)
        AuthStorage.clearAnonymousUserId()
        _state.value = AuthState.Authenticated(
            userId = account.userId,
            email = account.email,
            isAnonymous = false,
        )
    }.onFailure { failure ->
        log.e(failure) { "Connexion Kovelyx impossible" }
        _error.value = failure.message?.takeIf(String::isNotBlank)
            ?: getString(Res.string.auth_sign_in_failed)
    }

    suspend fun signOut(): Result<Unit> {
        _error.value = null
        // Clear the local state first; a network outage must never block logout.
        val oldBearer = CloudflareAccountClient.currentSession?.bearerToken
        CloudflareAccountClient.clearLocalSession()
        AuthStorage.clearAnonymousUserId()
        _state.value = AuthState.Unauthenticated
        if (!oldBearer.isNullOrBlank()) {
            scope.launch {
                try {
                    CloudflareAccountClient.revokePreviousSession(oldBearer)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.w(error) { "Déconnexion locale réussie ; révocation du jeton à réessayer côté serveur" }
                }
            }
        }
        return Result.success(Unit)
    }

    suspend fun prepareForServerSwitch(): Result<Unit> = runCatching {
        _error.value = null
        CloudflareAccountClient.clearLocalSession()
        AuthStorage.clearAnonymousUserId()
        _state.value = AuthState.Unauthenticated
    }

    fun reinitialize() {
        initialized = false
        _state.value = AuthState.Loading
        initialize()
    }

    /**
     * The retired backend cannot silently sign the user out on an unrelated
     * RPC error. Cloudflare validates its own sessions through /v1/me.
     */
    suspend fun signOutIfSessionInvalid(error: Throwable, source: String): Boolean = false

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        _error.value = null
        error("Suppression du compte Cloudflare indisponible : aucune donnée distante n'a été effacée.")
    }.onFailure { failure ->
        log.e(failure) { "Suppression de compte non effectuée" }
        _error.value = failure.message ?: getString(Res.string.auth_account_deletion_failed)
    }

    fun clearError() {
        _error.value = null
    }
}
