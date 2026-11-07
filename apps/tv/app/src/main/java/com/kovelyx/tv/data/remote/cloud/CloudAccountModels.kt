package com.kovelyx.tv.data.remote.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Models used by the independent Kovelyx account and profile APIs. */
@Serializable
data class ClaimSyncResult(
    @SerialName("result_owner_id") val ownerId: String? = null,
    val success: Boolean,
    val message: String
)

@Serializable
data class LinkedDevice(
    val id: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("device_user_id") val deviceUserId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("linked_at") val linkedAt: String? = null
)

@Serializable
data class TvLoginStartResult(
    val code: String,
    @SerialName("web_url") val webUrl: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 3
)

@Serializable
data class DeviceLoginStartResult(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 3,
    val legacy: Boolean = false
)

@Serializable
data class TvLoginPollResult(
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int? = null
)

@Serializable
data class ProfilePinVerifyResult(
    val unlocked: Boolean = false,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Int = 0
)
