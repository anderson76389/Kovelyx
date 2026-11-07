package com.kovelyx.tv.core.sync

import com.kovelyx.tv.core.profile.ProfileManager
import com.kovelyx.tv.data.local.PluginDataStore
import com.kovelyx.tv.domain.model.RemotePluginInfo
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Plugin repositories use the independent D1 profile preferences channel. */
@Singleton
class PluginSyncService @Inject constructor(
    private val cloud: CloudflareTvPreferencesSync,
    private val pluginDataStore: PluginDataStore,
    private val profileManager: ProfileManager
) {
    fun markCloudChange() { cloud.edited() }

    suspend fun pushToRemote(): Result<Unit> = runCatching {
        cloud.edited()
        check(cloud.synchronize(profileManager.activeProfileId.value)) {
            "Synchronisation Cloudflare des plugins différée"
        }
    }

    suspend fun getRemoteRepoUrls(): Result<List<RemotePluginInfo>> = runCatching {
        check(cloud.synchronize(profileManager.activeProfileId.value)) {
            "Synchronisation Cloudflare des plugins différée"
        }
        pluginDataStore.repositories.first().map { RemotePluginInfo(it.url, it.type.name) }
    }
}
