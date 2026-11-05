package com.nuvio.tv.core.sync

import com.nuvio.tv.core.profile.ProfileManager
import javax.inject.Inject
import javax.inject.Singleton

/** Debrid and external provider API keys remain on this TV, never uploaded to obsolete SQL RPCs. */
@Singleton
class ProviderCredentialSyncService @Inject constructor(
    private val profileManager: ProfileManager
) {
    suspend fun syncFromRemote(profileId: Int = profileManager.activeProfileId.value): Result<Boolean> =
        Result.success(false)

    fun requestForegroundPull(force: Boolean = false) = Unit
}
