package com.kovelyx.tv.core.sync

import com.kovelyx.tv.core.auth.CloudflareTvAccountClient
import com.kovelyx.tv.core.profile.ProfileManager
import com.kovelyx.tv.data.remote.cloud.ProfilePinVerifyResult
import com.kovelyx.tv.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

private const val PROFILE_PULL_MIN_INTERVAL_MS = 10_000L

internal data class ProfilePullFreshness(
    val userId: String? = null,
    val pulledAtMs: Long = 0L
) {
    fun isRecent(candidateUserId: String, nowMs: Long): Boolean =
        userId == candidateUserId && pulledAtMs > 0L &&
            nowMs >= pulledAtMs && nowMs - pulledAtMs < PROFILE_PULL_MIN_INTERVAL_MS
}

sealed class SetProfilePinResult {
    object Success : SetProfilePinResult()
    object CurrentPinRequired : SetProfilePinResult()
    data class Failure(val throwable: Throwable) : SetProfilePinResult()
}

/** Profiles and PIN locks are owned solely by the private Kovelyx Cloudflare worker. */
@Singleton
class ProfileSyncService @Inject constructor(
    private val profileManager: ProfileManager
) {
    suspend fun pushToRemote(): Result<Unit> = Result.success(Unit)

    suspend fun pullFromRemote(force: Boolean = false): Result<List<UserProfile>> =
        Result.success(profileManager.profiles.value)

    /** The caller must use CloudflareTvAccountClient.deleteCloudProfile, with remote acknowledgement. */
    suspend fun deleteProfileData(profileId: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Utiliser la suppression de profil Cloudflare confirmée"))

    suspend fun pullProfileLockStates(): Result<Map<Int, Boolean>> = runCatching {
        val pins = CloudflareTvAccountClient.cloudProfilePinStates()
        profileManager.profiles.value.associate { profile ->
            profile.id to (pins[profile.cloudProfileId] == true)
        }
    }

    suspend fun setProfilePin(profileId: Int, pin: String, currentPin: String? = null): SetProfilePinResult =
        try {
            val uuid = profileManager.profiles.value.firstOrNull { it.id == profileId }?.cloudProfileId
                ?: error("Profil Cloudflare non lié")
            if (CloudflareTvAccountClient.setCloudProfilePin(uuid, pin, currentPin))
                SetProfilePinResult.Success else SetProfilePinResult.CurrentPinRequired
        } catch (error: Exception) {
            SetProfilePinResult.Failure(error)
        }

    suspend fun clearProfilePin(profileId: Int, currentPin: String? = null): Result<Unit> = runCatching {
        val uuid = profileManager.profiles.value.firstOrNull { it.id == profileId }?.cloudProfileId
            ?: error("Profil Cloudflare non lié")
        CloudflareTvAccountClient.clearCloudProfilePin(uuid, currentPin)
    }

    suspend fun verifyProfilePin(profileId: Int, pin: String): Result<ProfilePinVerifyResult> = runCatching {
        val uuid = profileManager.profiles.value.firstOrNull { it.id == profileId }?.cloudProfileId
            ?: error("Profil Cloudflare non lié")
        val (unlocked, retry) = CloudflareTvAccountClient.verifyCloudProfilePin(uuid, pin)
        ProfilePinVerifyResult(unlocked = unlocked, retryAfterSeconds = retry)
    }
}
