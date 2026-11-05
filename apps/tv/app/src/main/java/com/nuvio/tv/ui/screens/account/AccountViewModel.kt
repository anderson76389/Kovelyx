package com.nuvio.tv.ui.screens.account

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.auth.diagnostics.AuthDiagnosticsSession
import com.nuvio.tv.core.logging.bodySnippetForLog
import com.nuvio.tv.core.logging.diagnosticSummary
import com.nuvio.tv.core.logging.rawForLog
import com.nuvio.tv.core.logging.urlForLog
import com.nuvio.tv.core.plugin.PluginManager
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.sync.AddonSyncService
import com.nuvio.tv.core.sync.LibrarySyncService
import com.nuvio.tv.core.sync.PluginSyncService
import com.nuvio.tv.core.sync.WatchProgressSyncService
import com.nuvio.tv.core.sync.WatchedItemsSyncService
import com.nuvio.tv.core.sync.ProfileSettingsSyncService
import com.nuvio.tv.core.sync.ProviderCredentialSyncService
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.repository.AddonRepositoryImpl
import com.nuvio.tv.data.repository.AuthDiagnosticReportRepository
import com.nuvio.tv.data.repository.LibraryRepositoryImpl
import com.nuvio.tv.data.repository.WatchProgressRepositoryImpl
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.domain.model.ServerConfiguration
import com.nuvio.tv.domain.repository.SyncRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private const val TAG = "AccountViewModel"
private const val QR_ENDPOINT_START = "/rest/v1/rpc/start_device_login_session"
private const val QR_ENDPOINT_POLL = "/rest/v1/rpc/poll_tv_login_session"
private const val QR_ENDPOINT_EXCHANGE = "/functions/v1/tv-logins-exchange"
private val qrLoginTraceCounter = AtomicLong(0L)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val syncRepository: SyncRepository,
    private val pluginSyncService: PluginSyncService,
    private val addonSyncService: AddonSyncService,
    private val watchProgressSyncService: WatchProgressSyncService,
    private val librarySyncService: LibrarySyncService,
    private val watchedItemsSyncService: WatchedItemsSyncService,
    private val profileSettingsSyncService: ProfileSettingsSyncService,
    private val providerCredentialSyncService: ProviderCredentialSyncService,
    private val pluginManager: PluginManager,
    private val addonRepository: AddonRepositoryImpl,
    private val watchProgressRepository: WatchProgressRepositoryImpl,
    private val libraryRepository: LibraryRepositoryImpl,
    private val watchProgressPreferences: WatchProgressPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val watchedItemsPreferences: WatchedItemsPreferences,
    private val traktAuthDataStore: TraktAuthDataStore,
    private val cloudSync: com.nuvio.tv.core.sync.CloudflareTvDataSync,
    private val profileManager: ProfileManager,
    private val authDiagnosticReportRepository: AuthDiagnosticReportRepository,
    private val serverConfiguration: ServerConfiguration,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {


    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
    private var qrLoginPollJob: Job? = null
    private var activeQrLoginTraceId: Long? = null
    private var activeQrLoginDiagnostics: AuthDiagnosticsSession? = null
    private var qrLoginPollAttempt: Int = 0
    private var qrLoginExchangeInFlight: Boolean = false

    val usesEmailPasswordLogin: Boolean
        get() = CloudflareTvAccountClient.enabled ||
            (serverConfiguration.isCustom && serverConfiguration.capabilities.emailPasswordAuth)

    init {
        observeAuthState()
        observeProfileNames()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authManager.authState.collect { state ->
                _uiState.update {
                    it.copy(
                        authState = state,
                        effectiveOwnerId = if (state is AuthState.SignedOut || state is AuthState.Loading) null else it.effectiveOwnerId,
                        connectedStats = if (state is AuthState.FullAccount) it.connectedStats else null,
                        isStatsLoading = if (state is AuthState.FullAccount) it.isStatsLoading else false
                    )
                }
                updateEffectiveOwnerId(state)
                if (state is AuthState.FullAccount) {
                    if (CloudflareTvAccountClient.enabled) loadCloudflareProfilesReadOnly()
                    else {
                        loadConnectedStats()
                        loadSyncOverview()
                    }
                } else if (CloudflareTvAccountClient.enabled) {
                    _uiState.update { it.copy(cloudProfileNames = emptyList()) }
                }
            }
        }
    }

    private fun observeProfileNames() {
        viewModelScope.launch {
            profileManager.profiles.collect { profiles ->
                val current = _uiState.value.syncOverview ?: return@collect
                val updated = current.copy(
                    perProfile = current.perProfile.map { stat ->
                        val local = profiles.firstOrNull { it.id == stat.profileId }
                        if (local != null) {
                            stat.copy(profileName = local.name, avatarColorHex = local.avatarColorHex)
                        } else stat
                    }
                )
                _uiState.update { it.copy(syncOverview = updated) }
            }
        }
    }

    private fun loadCloudflareProfilesReadOnly() {
        viewModelScope.launch {
            runCatching { CloudflareTvAccountClient.getCloudProfileNames() }
                .onSuccess { names ->
                    _uiState.update { it.copy(cloudProfileNames = names) }
                }
                .onFailure { error ->
                    Log.w(TAG, "Kovelyx Cloud : lecture profils indisponible ; aucun changement local", error)
                }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authManager.signUpWithEmail(email, password).fold(
                onSuccess = {
                    // L'inscription n'est pas une connexion; les anciennes RPC ne sont
                    // pas compatibles D1. Ne transférer aucune donnée sans confirmation.
                    if (!CloudflareTvAccountClient.enabled) pushLocalDataToRemote()
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = userFriendlyError(e)) }
                }
            )
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authManager.signInWithEmail(email, password).fold(
                onSuccess = {
                    if (!CloudflareTvAccountClient.enabled) {
                        pullRemoteData().onFailure { e ->
                            Log.e("AccountViewModel", "signIn: pullRemoteData failed, continuing signed-in flow", e)
                        }
                        loadConnectedStats()
                    }
                    // Le compte Cloudflare reste connecté sans utiliser d'ancien protocole RPC
                    // ni écraser les bibliothèques locales avant la migration D1.
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = userFriendlyError(e)) }
                }
            )
        }
    }

    fun generateSyncCode(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (!authManager.isAuthenticated) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.account_error_signin_required)) }
                return@launch
            }
            pushLocalDataToRemote()
            syncRepository.generateSyncCode(pin).fold(
                onSuccess = { code ->
                    _uiState.update { it.copy(isLoading = false, generatedSyncCode = code) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = userFriendlyError(e)) }
                }
            )
        }
    }

    fun getSyncCode(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            syncRepository.getSyncCode(pin).fold(
                onSuccess = { code ->
                    _uiState.update { it.copy(isLoading = false, generatedSyncCode = code) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = userFriendlyError(e)) }
                }
            )
        }
    }

    fun claimSyncCode(code: String, pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (!authManager.isAuthenticated) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.account_error_signin_required)) }
                return@launch
            }
            syncRepository.claimSyncCode(code, pin, Build.MODEL).fold(
                onSuccess = { result ->
                    if (result.success) {
                        authManager.clearEffectiveUserIdCache()
                        pullRemoteData().onFailure { e ->
                            Log.e("AccountViewModel", "claimSyncCode: pullRemoteData failed, continuing", e)
                        }
                        updateEffectiveOwnerId(_uiState.value.authState)
                        _uiState.update { it.copy(isLoading = false, syncClaimSuccess = true) }
                    } else {
                        authManager.signOut(explicit = false)
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                },
                onFailure = { e ->
                    authManager.signOut(explicit = false)
                    _uiState.update { it.copy(isLoading = false, error = userFriendlyError(e)) }
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _uiState.update { it.copy(connectedStats = null, isStatsLoading = false) }
        }
    }

    fun loadLinkedDevices() {
        if (CloudflareTvAccountClient.enabled) return
        viewModelScope.launch {
            syncRepository.getLinkedDevices().fold(
                onSuccess = { devices ->
                    _uiState.update { it.copy(linkedDevices = devices) }
                },
                onFailure = { /* silently handle */ }
            )
        }
    }

    fun unlinkDevice(deviceUserId: String) {
        viewModelScope.launch {
            syncRepository.unlinkDevice(deviceUserId)
            loadLinkedDevices()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSyncClaimSuccess() {
        _uiState.update { it.copy(syncClaimSuccess = false) }
    }

    fun clearGeneratedSyncCode() {
        _uiState.update { it.copy(generatedSyncCode = null) }
    }

    fun startQrLogin() {
        viewModelScope.launch {
            val traceId = qrLoginTraceCounter.incrementAndGet()
            val startedAtMs = SystemClock.elapsedRealtime()
            finishActiveQrDiagnostics(status = "cancelled", reason = "qr_login_replaced")
            cancelQrLoginPolling()
            activeQrLoginTraceId = traceId
            val diagnostics = AuthDiagnosticsSession(
                authDiagnosticReportRepository,
                "qr_login",
                qrTraceId = traceId,
                serverConfiguration = serverConfiguration
            )
            activeQrLoginDiagnostics = diagnostics
            qrLoginPollAttempt = 0
            qrLoginExchangeInFlight = false
            val nonce = generateDeviceNonce()
            diagnostics.recordState(
                "qr_login_start",
                mapOf(
                    "authState" to _uiState.value.authState.nameForLog(),
                    "deviceModel" to Build.MODEL,
                    "redirectBaseUrl" to serverConfiguration.deviceLoginWebBaseUrl,
                    "legacyRedirectBaseUrl" to serverConfiguration.tvLoginWebBaseUrl,
                    "historicalBackendUrl" to serverConfiguration.backendUrl,
                    "anonKeyConfigured" to serverConfiguration.publishableKey.isNotBlank().toString(),
                    "nonce" to nonce
                )
            )
            Log.d(
                TAG,
                "QR_LOGIN[$traceId] start requested auth=${_uiState.value.authState.nameForLog()} model=${Build.MODEL.bodySnippetForLog()} redirect=${serverConfiguration.deviceLoginWebBaseUrl.urlForLog()} legacyRedirect=${serverConfiguration.tvLoginWebBaseUrl.urlForLog()} ancienBackend=${serverConfiguration.backendUrl.urlForLog()} anonKeyConfigured=${serverConfiguration.publishableKey.isNotBlank()} nonce=${nonce.rawForLog()}"
            )
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    qrLoginCode = null,
                    qrLoginUserCode = null,
                    qrLoginUrl = null,
                    qrLoginVerificationUri = null,
                    qrLoginNonce = nonce,
                    qrLoginBitmap = null,
                    qrLoginStatus = context.getString(R.string.qr_login_preparing),
                    qrLoginExpiresAtMillis = null
                )
            }
            Log.d(TAG, "QR_LOGIN[$traceId] start_device_login_session call begin")
            authManager.startDeviceLoginSession(
                deviceNonce = nonce,
                deviceName = Build.MODEL,
                deviceType = "tv",
                redirectBaseUrl = serverConfiguration.deviceLoginWebBaseUrl.orEmpty(),
                legacyRedirectBaseUrl = serverConfiguration.tvLoginWebBaseUrl.orEmpty(),
                traceId = traceId,
                diagnostics = diagnostics
            ).fold(
                onSuccess = { result ->
                    val expiresAtMillis = parseTimestampMillis(result.expiresAt)
                    if (result.deviceCode.isBlank() || result.userCode.isBlank() || result.verificationUriComplete.isBlank()) {
                        Log.w(TAG, "QR_LOGIN[$traceId] start_device_login_session returned incomplete data deviceCodeBlank=${result.deviceCode.isBlank()} userCodeBlank=${result.userCode.isBlank()} urlBlank=${result.verificationUriComplete.isBlank()}")
                    }
                    val qrStartedAtMs = SystemClock.elapsedRealtime()
                    val qrBitmap = runCatching { QrCodeGenerator.generate(result.verificationUriComplete, 420, margin = 1) }
                        .onFailure { e ->
                            Log.e(TAG, "QR_LOGIN[$traceId] QR bitmap generation failed url=${result.verificationUriComplete.urlForLog()} urlLength=${result.verificationUriComplete.length} error=${e.diagnosticSummary()}", e)
                        }
                        .getOrNull()
                    Log.d(
                        TAG,
                        "QR_LOGIN[$traceId] start_device_login_session ok totalElapsedMs=${SystemClock.elapsedRealtime() - startedAtMs} deviceCode=${result.deviceCode.rawForLog()} userCode=${result.userCode.rawForLog()} url=${result.verificationUriComplete.urlForLog()} urlLength=${result.verificationUriComplete.length} legacy=${result.legacy} expiresAt=${result.expiresAt} expiresAtMs=${expiresAtMillis ?: "-"} pollInterval=${result.pollIntervalSeconds} qrBitmap=${qrBitmap != null} qrElapsedMs=${SystemClock.elapsedRealtime() - qrStartedAtMs}"
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            qrLoginCode = result.deviceCode,
                            qrLoginUserCode = result.userCode,
                            qrLoginUrl = result.verificationUriComplete,
                            qrLoginVerificationUri = result.verificationUri,
                            qrLoginBitmap = qrBitmap,
                            qrLoginStatus = context.getString(R.string.qr_login_scan_prompt),
                            qrLoginExpiresAtMillis = expiresAtMillis,
                            qrLoginPollIntervalSeconds = result.pollIntervalSeconds.coerceAtLeast(2)
                        )
                    }
                    startQrLoginPolling()
                },
                onFailure = { e ->
                    Log.e(TAG, "QR_LOGIN[$traceId] start_device_login_session failed totalElapsedMs=${SystemClock.elapsedRealtime() - startedAtMs} error=${e.diagnosticSummary()}", e)
                    diagnostics.finishFailure("start_device_login_session_failed", QR_ENDPOINT_START, error = e)
                    activeQrLoginDiagnostics = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = userFriendlyError(e),
                            qrLoginStatus = context.getString(R.string.qr_login_start_failed)
                        )
                    }
                }
            )
        }
    }

    fun pollQrLogin() {
        viewModelScope.launch {
            Log.d(TAG, "QR_LOGIN[${activeQrLoginTraceId ?: "-"}] manual poll requested")
            pollQrLoginOnce()
        }
    }

    fun exchangeQrLogin() {
        viewModelScope.launch {
            val current = _uiState.value
            val traceId = activeQrLoginTraceId
            val diagnostics = activeQrLoginDiagnostics
            val code = current.qrLoginCode
            val nonce = current.qrLoginNonce
            if (code == null || nonce == null) {
                Log.w(TAG, "QR_LOGIN[${traceId ?: "-"}] exchange skipped missing code=${code != null} nonce=${nonce != null}")
                return@launch
            }
            if (qrLoginExchangeInFlight) {
                Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] exchange skipped because another exchange is in flight")
                return@launch
            }
            qrLoginExchangeInFlight = true
            val startedAtMs = SystemClock.elapsedRealtime()
            Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] exchange begin code=${code.rawForLog()} nonce=${nonce.rawForLog()} auth=${current.authState.nameForLog()}")
            _uiState.update { it.copy(isLoading = true, error = null, qrLoginStatus = context.getString(R.string.qr_login_signing_in)) }
            try {
                authManager.exchangeTvLoginSession(code = code, deviceNonce = nonce, traceId = traceId, diagnostics = diagnostics).fold(
                    onSuccess = {
                        Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] exchange ok elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
                        diagnostics?.finishSuccess("qr_login_completed")
                        if (activeQrLoginDiagnostics === diagnostics) activeQrLoginDiagnostics = null
                        pullRemoteData().onFailure { e ->
                            Log.e(TAG, "QR_LOGIN[${traceId ?: "-"}] exchange pullRemoteData failed, continuing error=${e.diagnosticSummary()}", e)
                        }
                        loadConnectedStats()
                        _uiState.update { it.copy(isLoading = false, qrLoginStatus = context.getString(R.string.qr_login_success)) }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "QR_LOGIN[${traceId ?: "-"}] exchange failed elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs} error=${e.diagnosticSummary()}", e)
                        diagnostics?.finishFailure("exchange_tv_login_session_failed", QR_ENDPOINT_EXCHANGE, error = e)
                        if (activeQrLoginDiagnostics === diagnostics) activeQrLoginDiagnostics = null
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = userFriendlyError(e),
                                qrLoginStatus = context.getString(R.string.qr_login_exchange_failed)
                            )
                        }
                    }
                )
            } finally {
                qrLoginExchangeInFlight = false
            }
        }
    }

    fun clearQrLoginSession() {
        Log.d(TAG, "QR_LOGIN[${activeQrLoginTraceId ?: "-"}] clearing session code=${_uiState.value.qrLoginCode.rawForLog()} pollAttempts=$qrLoginPollAttempt exchangeInFlight=$qrLoginExchangeInFlight")
        val diagnostics = activeQrLoginDiagnostics
        activeQrLoginDiagnostics = null
        if (diagnostics != null && !diagnostics.isFinished()) {
            viewModelScope.launch {
                diagnostics.finishTerminal(status = "cancelled", reason = "qr_login_cleared", failingEndpoint = null)
            }
        }
        cancelQrLoginPolling()
        activeQrLoginTraceId = null
        qrLoginPollAttempt = 0
        qrLoginExchangeInFlight = false
        _uiState.update {
            it.copy(
                qrLoginCode = null,
                qrLoginUserCode = null,
                qrLoginUrl = null,
                qrLoginVerificationUri = null,
                qrLoginNonce = null,
                qrLoginBitmap = null,
                qrLoginStatus = null,
                qrLoginExpiresAtMillis = null
            )
        }
    }

    private suspend fun finishActiveQrDiagnostics(status: String, reason: String) {
        val diagnostics = activeQrLoginDiagnostics
        activeQrLoginDiagnostics = null
        if (diagnostics != null && !diagnostics.isFinished()) {
            diagnostics.finishTerminal(status = status, reason = reason, failingEndpoint = null)
        }
    }

    private suspend fun updateEffectiveOwnerId(state: AuthState) {
        val currentUserId = when (state) {
            is AuthState.FullAccount -> state.userId
            else -> null
        }
        if (currentUserId == null) return

        val effectiveOwnerId = authManager.getEffectiveUserId() ?: currentUserId
        _uiState.update { it.copy(effectiveOwnerId = effectiveOwnerId) }
    }

    private fun loadConnectedStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isStatsLoading = true) }

            val stats = runCatching {
                val addonsCount = addonRepository.getInstalledAddons().first().size
                val pluginsCount = pluginManager.repositories.first().size
                val libraryCount = libraryPreferences.getAllItems().size
                val watchProgressCount = watchProgressRepository.allProgress.first().size
                AccountConnectedStats(
                    addons = addonsCount,
                    plugins = pluginsCount,
                    library = libraryCount,
                    watchProgress = watchProgressCount
                )
            }.getOrNull()

            _uiState.update {
                it.copy(
                    connectedStats = stats ?: it.connectedStats,
                    isStatsLoading = false
                )
            }
        }
    }

    @Serializable
    private data class SyncOverviewResponse(
        val addons: Map<String, Int> = emptyMap(),
        val plugins: Map<String, Int> = emptyMap(),
        @SerialName("library_items") val libraryItems: Map<String, Int> = emptyMap(),
        @SerialName("watch_progress") val watchProgress: Map<String, Int> = emptyMap(),
        @SerialName("watched_items") val watchedItems: Map<String, Int> = emptyMap(),
        val profiles: Map<String, ProfileInfo> = emptyMap()
    ) {
        @Serializable
        data class ProfileInfo(
            val name: String,
            val color: String
        )
    }

    fun loadSyncOverview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncOverviewLoading = true) }
            val overview = runCatching {
                val remoteProfiles = CloudflareTvAccountClient.cloudProfiles()
                val local = profileManager.profiles.value
                val stats = remoteProfiles.mapNotNull { (uuid, name) ->
                    val profile = local.firstOrNull { it.cloudProfileId == uuid } ?: return@mapNotNull null
                    val rows = CloudflareTvAccountClient.syncRows(uuid)
                    fun count(category: String) = rows.count {
                        it.optString("category") == category && !it.optBoolean("deleted")
                    }
                    val addons = rows.firstOrNull {
                        it.optString("category") == "addons" && it.optString("itemKey") == "installed"
                    }?.optJSONObject("payload")?.optJSONArray("items")?.length() ?: 0
                    val plugins = rows.firstOrNull {
                        it.optString("category") == "profile_settings" &&
                            it.optString("itemKey") == "plugin_repositories_v1"
                    }?.optJSONObject("payload")?.optJSONArray("repositories")?.length() ?: 0
                    ProfileSyncStats(
                        profileId = profile.id,
                        profileName = name,
                        avatarColorHex = profile.avatarColorHex,
                        addons = addons,
                        plugins = plugins,
                        library = count("library"),
                        watchProgress = count("watch_progress"),
                        watchedItems = count("watched_items")
                    )
                }
                SyncOverview(
                    profileCount = remoteProfiles.size,
                    totalAddons = stats.sumOf { it.addons },
                    totalPlugins = stats.sumOf { it.plugins },
                    totalLibrary = stats.sumOf { it.library },
                    totalWatchProgress = stats.sumOf { it.watchProgress },
                    totalWatchedItems = stats.sumOf { it.watchedItems },
                    perProfile = stats
                )
            }.getOrNull()
            _uiState.update { it.copy(syncOverview = overview ?: it.syncOverview, isSyncOverviewLoading = false) }
        }
    }

    private fun userFriendlyError(e: Throwable): String {
        val raw = e.message ?: ""
        val message = raw.lowercase()
        val compactRaw = raw.bodySnippetForLog()
        Log.w(TAG, "Raw error: $compactRaw")

        val resId = when {
            // PIN errors (from PG RAISE EXCEPTION or any wrapper)
            message.contains("incorrect pin") || message.contains("invalid pin") || message.contains("wrong pin") -> R.string.account_error_incorrect_pin

            // Sync code errors
            message.contains("expired") -> R.string.account_error_sync_code_expired
            message.contains("invalid") && message.contains("code") -> R.string.account_error_invalid_sync_code
            message.contains("not found") || message.contains("no sync code") -> R.string.account_error_sync_code_not_found
            message.contains("already linked") -> R.string.account_error_device_already_linked
            message.contains("empty response") -> R.string.account_error_generic_retry

            // Auth errors
            message.contains("invalid login credentials") -> R.string.account_error_invalid_credentials
            message.contains("email not confirmed") -> R.string.account_error_email_not_confirmed
            message.contains("user already registered") -> R.string.account_error_email_already_registered
            message.contains("invalid email") -> R.string.account_error_invalid_email
            message.contains("password") && message.contains("short") -> R.string.account_error_password_too_short
            message.contains("password") && message.contains("weak") -> R.string.account_error_password_too_weak
            message.contains("signup is disabled") -> R.string.account_error_signup_disabled
            message.contains("rate limit") || message.contains("too many requests") -> R.string.account_error_rate_limited
            message.contains("tv login") && message.contains("expired") -> R.string.account_error_qr_login_expired
            message.contains("tv login") && message.contains("invalid") -> R.string.account_error_invalid_qr_login
            message.contains("tv login") && message.contains("nonce") -> R.string.account_error_qr_login_other_device
            message.contains("start_tv_login_session") && message.contains("could not find the function") ->
                R.string.account_error_qr_login_outdated
            message.contains("gen_random_bytes") && message.contains("does not exist") ->
                R.string.account_error_qr_login_missing_setup
            message.contains("invalid tv login redirect base url") ->
                R.string.account_error_qr_login_misconfigured
            message.contains("invalid device nonce") ->
                R.string.account_error_qr_login_invalid_request

            // Network errors
            message.contains("unable to resolve host") || message.contains("no address associated") -> R.string.account_error_no_internet
            message.contains("timeout") || message.contains("timed out") -> R.string.account_error_connection_timeout
            message.contains("connection refused") || message.contains("connect failed") -> R.string.account_error_connection_refused

            // Auth state
            message.contains("not authenticated") -> R.string.account_error_not_authenticated

            // HTTP errors (404 for unavailable resources, 400 for invalid requests)
            message.contains("404") || message.contains("could not find") -> R.string.account_error_service_unavailable
            message.contains("400") || message.contains("bad request") -> R.string.account_error_invalid_request

            // Fallback
            else -> R.string.account_error_unexpected
        }
        return context.getString(resId)
    }

    private fun startQrLoginPolling() {
        cancelQrLoginPolling()
        val traceId = activeQrLoginTraceId
        qrLoginPollAttempt = 0
        Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] polling started intervalSeconds=${_uiState.value.qrLoginPollIntervalSeconds.coerceAtLeast(2)} code=${_uiState.value.qrLoginCode.rawForLog()}")
        qrLoginPollJob = viewModelScope.launch {
            while (isActive) {
                val interval = _uiState.value.qrLoginPollIntervalSeconds.coerceAtLeast(2)
                Log.d(TAG, "QR_LOGIN[${activeQrLoginTraceId ?: "-"}] polling delay intervalSeconds=$interval nextAttempt=${qrLoginPollAttempt + 1}")
                delay(interval * 1000L)
                pollQrLoginOnce()
            }
        }
    }

    private fun cancelQrLoginPolling() {
        if (qrLoginPollJob != null) {
            Log.d(TAG, "QR_LOGIN[${activeQrLoginTraceId ?: "-"}] polling cancelled attempts=$qrLoginPollAttempt")
        }
        qrLoginPollJob?.cancel()
        qrLoginPollJob = null
    }

    private fun generateDeviceNonce(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private suspend fun pollQrLoginOnce() {
        val current = _uiState.value
        val traceId = activeQrLoginTraceId
        val diagnostics = activeQrLoginDiagnostics
        val code = current.qrLoginCode
        val nonce = current.qrLoginNonce
        if (code == null || nonce == null) {
            Log.w(TAG, "QR_LOGIN[${traceId ?: "-"}] poll skipped missing code=${code != null} nonce=${nonce != null}")
            return
        }
        val attempt = ++qrLoginPollAttempt
        val startedAtMs = SystemClock.elapsedRealtime()
        Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] poll attempt=$attempt begin code=${code.rawForLog()} nonce=${nonce.rawForLog()}")
        authManager.pollTvLoginSession(code = code, deviceNonce = nonce, traceId = traceId, attempt = attempt, diagnostics = diagnostics).fold(
            onSuccess = { result ->
                val normalizedStatus = result.status.lowercase()
                val expiresAtMillis = result.expiresAt?.let(::parseTimestampMillis)
                Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] poll attempt=$attempt ok status=${result.status} normalized=$normalizedStatus expiresAt=${result.expiresAt ?: "-"} expiresAtMs=${expiresAtMillis ?: "-"} nextInterval=${result.pollIntervalSeconds ?: current.qrLoginPollIntervalSeconds} elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs}")
                _uiState.update {
                    it.copy(
                        qrLoginStatus = when (normalizedStatus) {
                            "approved" -> context.getString(R.string.qr_login_approved)
                            "pending" -> context.getString(R.string.qr_login_pending)
                            "expired" -> context.getString(R.string.qr_login_expired)
                            else -> "Status: ${result.status}"
                        },
                        qrLoginExpiresAtMillis = expiresAtMillis ?: it.qrLoginExpiresAtMillis,
                        qrLoginPollIntervalSeconds = (result.pollIntervalSeconds ?: it.qrLoginPollIntervalSeconds).coerceAtLeast(2)
                    )
                }
                when (normalizedStatus) {
                    "approved" -> {
                        Log.d(TAG, "QR_LOGIN[${traceId ?: "-"}] poll attempt=$attempt approved; starting exchange")
                        cancelQrLoginPolling()
                        exchangeQrLogin()
                    }
                    "expired", "used", "cancelled" -> {
                        Log.w(TAG, "QR_LOGIN[${traceId ?: "-"}] poll attempt=$attempt terminal status=$normalizedStatus")
                        cancelQrLoginPolling()
                        diagnostics?.finishTerminal(status = normalizedStatus, reason = "qr_login_$normalizedStatus", failingEndpoint = QR_ENDPOINT_POLL)
                        if (activeQrLoginDiagnostics === diagnostics) activeQrLoginDiagnostics = null
                    }
                }
            },
            onFailure = { e ->
                Log.e(TAG, "QR_LOGIN[${traceId ?: "-"}] poll attempt=$attempt failed elapsedMs=${SystemClock.elapsedRealtime() - startedAtMs} error=${e.diagnosticSummary()}", e)
                cancelQrLoginPolling()
                diagnostics?.finishFailure("poll_tv_login_session_failed", QR_ENDPOINT_POLL, error = e)
                if (activeQrLoginDiagnostics === diagnostics) activeQrLoginDiagnostics = null
                _uiState.update { it.copy(error = userFriendlyError(e)) }
            }
        )
    }

    private suspend fun pushLocalDataToRemote() {
        if (CloudflareTvAccountClient.account != null)
            cloudSync.synchronize(profileManager.activeProfileId.value)
    }

    private suspend fun pullRemoteData(): Result<Unit> = runCatching {
        check(CloudflareTvAccountClient.account != null) { "Compte Cloudflare non connecté" }
        cloudSync.synchronize(profileManager.activeProfileId.value)
    }

    override fun onCleared() {
        cancelQrLoginPolling()
        super.onCleared()
    }
}

private fun AuthState.nameForLog(): String =
    when (this) {
        is AuthState.FullAccount -> "FullAccount(${userId.rawForLog()})"
        AuthState.Loading -> "Loading"
        AuthState.SignedOut -> "SignedOut"
    }

private fun parseTimestampMillis(value: String): Long? =
    runCatching { OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli() }.getOrNull()
