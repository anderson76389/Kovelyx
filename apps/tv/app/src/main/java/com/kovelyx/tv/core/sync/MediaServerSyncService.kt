package com.kovelyx.tv.core.sync

import com.kovelyx.tv.core.profile.ProfileManager
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps media server tokens private on device until an encrypted D1 sync exists. */
@Singleton
class MediaServerSyncService @Inject constructor(
    private val profileManager: ProfileManager
) {
    suspend fun syncFromRemote(profileId: Int = profileManager.activeProfileId.value): Result<Boolean> =
        Result.success(false)

    fun requestForegroundPull(force: Boolean = false) = Unit
}
