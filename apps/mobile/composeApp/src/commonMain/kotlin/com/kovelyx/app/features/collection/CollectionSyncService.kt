package com.kovelyx.app.features.collection

import com.kovelyx.app.core.auth.AuthRepository
import com.kovelyx.app.core.auth.AuthState
import com.kovelyx.app.core.cloud.CloudflareAccountClient
import com.kovelyx.app.core.cloud.CloudflareDataSync
import com.kovelyx.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Collections use only the independent Cloudflare catalogue synchronizer. */
object CollectionSyncService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null
    var isSyncingFromRemote: Boolean = false

    @OptIn(FlowPreview::class)
    fun startObserving() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            CollectionRepository.localChangeEvents.debounce(1500L).collect {
                if (!isSyncingFromRemote) triggerPush()
            }
        }
    }

    suspend fun pullFromServer(profileId: Int) {
        if (CloudflareAccountClient.enabled && ProfileRepository.activeProfileId == profileId)
            CloudflareDataSync.request(profileId)
    }

    fun triggerPush() {
        if (isSyncingFromRemote || !CloudflareAccountClient.enabled) return
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return
        if (!account.isAnonymous) CloudflareDataSync.request(ProfileRepository.activeProfileId)
    }
}
