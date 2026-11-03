package com.nuvio.app.core.network

import androidx.compose.runtime.Composable
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.core.cloud.CloudflareAccountClient
import com.nuvio.app.core.cloud.CloudflareConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.details_check_connection
import nuvio.composeapp.generated.resources.details_servers_unreachable
import nuvio.composeapp.generated.resources.network_cannot_reach_servers
import nuvio.composeapp.generated.resources.network_connection_issue
import nuvio.composeapp.generated.resources.network_no_internet_connection
import nuvio.composeapp.generated.resources.network_please_check_connection
import org.jetbrains.compose.resources.stringResource

enum class NetworkCondition {
    Unknown,
    Checking,
    Online,
    NoInternet,
    ServersUnreachable,
}

data class NetworkStatusUiState(
    val condition: NetworkCondition = NetworkCondition.Unknown,
) {
    val isOnline: Boolean
        get() = condition == NetworkCondition.Online

    val isOfflineLike: Boolean
        get() = condition == NetworkCondition.NoInternet || condition == NetworkCondition.ServersUnreachable
}

@Composable
fun NetworkCondition.titleForEmptyState(): String =
    when (this) {
        NetworkCondition.ServersUnreachable -> stringResource(Res.string.network_cannot_reach_servers)
        NetworkCondition.NoInternet -> stringResource(Res.string.network_no_internet_connection)
        else -> stringResource(Res.string.network_connection_issue)
    }

@Composable
fun NetworkCondition.messageForEmptyState(): String =
    when (this) {
        NetworkCondition.ServersUnreachable -> stringResource(Res.string.details_servers_unreachable)
        NetworkCondition.NoInternet -> stringResource(Res.string.details_check_connection)
        else -> stringResource(Res.string.network_please_check_connection)
    }

object NetworkStatusRepository {
    private const val REQUEST_TIMEOUT_MS = 4_500L
    private const val FOREGROUND_REFRESH_DELAY_MS = 6_000L
    private const val FOREGROUND_FAILURE_CONFIRM_DELAY_MS = 2_000L
    private const val PUBLIC_PROBE_PRIMARY = "https://www.gstatic.com/generate_204"
    private const val PUBLIC_PROBE_FALLBACK = "https://cloudflare.com/cdn-cgi/trace"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(NetworkStatusUiState())
    val uiState: StateFlow<NetworkStatusUiState> = _uiState.asStateFlow()

    private var started = false
    private var probeInFlight = false
    private var pendingProbeAfterCurrent = false
    private var pendingProbeConfirmFailures = false
    private var foregroundRefreshJob: Job? = null

    fun ensureStarted() {
        if (started) return
        started = true
        requestRefresh(force = true)
    }

    fun requestForegroundRefresh() {
        ensureStarted()
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = scope.launch {
            delay(FOREGROUND_REFRESH_DELAY_MS)
            requestRefresh(force = true, confirmFailures = true)
        }
    }

    fun requestRefresh(force: Boolean = false, confirmFailures: Boolean = false) {
        if (!started) started = true
        if (probeInFlight) {
            if (force) {
                pendingProbeAfterCurrent = true
                pendingProbeConfirmFailures = pendingProbeConfirmFailures || confirmFailures
            }
            return
        }

        scope.launch {
            var nextConfirmFailures = confirmFailures
            do {
                val runConfirmFailures = nextConfirmFailures || pendingProbeConfirmFailures
                nextConfirmFailures = false
                pendingProbeAfterCurrent = false
                pendingProbeConfirmFailures = false
                probeInFlight = true
                runProbe(confirmFailures = runConfirmFailures)
                probeInFlight = false
            } while (pendingProbeAfterCurrent)
        }
    }

    private suspend fun runProbe(confirmFailures: Boolean) {
        if (_uiState.value.condition == NetworkCondition.Unknown) {
            _uiState.value = NetworkStatusUiState(condition = NetworkCondition.Checking)
        }

        val previousCondition = _uiState.value.condition
        var nextCondition = probeCondition()
        if (
            confirmFailures &&
            previousCondition == NetworkCondition.Online &&
            nextCondition.isOfflineLike()
        ) {
            delay(FOREGROUND_FAILURE_CONFIRM_DELAY_MS)
            nextCondition = probeCondition()
        }

        _uiState.value = NetworkStatusUiState(condition = nextCondition)
    }

    private suspend fun probeCondition(): NetworkCondition {
        // Check the backend actually used by this build before unrelated public probes.
        // A working Worker also proves connectivity when Google is blocked by the network.
        if (CloudflareAccountClient.enabled) {
            if (probeReachable(
                    CloudflareConfig.URL.trimEnd('/') + "/ready",
                    headers = mapOf("Accept" to "application/json", "User-Agent" to "Kovelyx-Client"),
                    requireSuccess = true,
                )) {
                return NetworkCondition.Online
            }
            return if (probePublicInternet()) NetworkCondition.ServersUnreachable else NetworkCondition.NoInternet
        }

        val internetReachable = probePublicInternet()
        if (!internetReachable) {
            return NetworkCondition.NoInternet
        }

        val endpoints = SupabaseEndpointConfig.restEndpointUrls()
        // Guest builds have no account backend to contact.
        if (endpoints.isEmpty()) return NetworkCondition.Online
        val supabaseReachable = endpoints.any { url ->
            probeReachable(
                url = url,
                headers = mapOf("apikey" to ServerConfigurationRepository.active.value.publishableKey),
            )
        }
        if (!supabaseReachable) {
            return NetworkCondition.ServersUnreachable
        }

        return NetworkCondition.Online
    }

    private suspend fun probePublicInternet(): Boolean =
        probeReachable(PUBLIC_PROBE_PRIMARY) || probeReachable(PUBLIC_PROBE_FALLBACK)

    private suspend fun probeReachable(
        url: String,
        headers: Map<String, String> = emptyMap(),
        requireSuccess: Boolean = false,
    ): Boolean {
        val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            runCatching {
                httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = headers,
                    body = "",
                )
            }.getOrNull()
        } ?: return false

        return if (requireSuccess) response.status in 200..299 else response.status in 100..599
    }

    private fun NetworkCondition.isOfflineLike(): Boolean =
        this == NetworkCondition.NoInternet || this == NetworkCondition.ServersUnreachable
}
