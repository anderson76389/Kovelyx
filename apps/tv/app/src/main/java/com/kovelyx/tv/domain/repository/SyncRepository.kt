package com.kovelyx.tv.domain.repository

import com.kovelyx.tv.data.remote.cloud.ClaimSyncResult
import com.kovelyx.tv.data.remote.cloud.LinkedDevice

interface SyncRepository {
    suspend fun generateSyncCode(pin: String): Result<String>
    suspend fun getSyncCode(pin: String): Result<String>
    suspend fun claimSyncCode(code: String, pin: String, deviceName: String?): Result<ClaimSyncResult>
    suspend fun unlinkDevice(deviceUserId: String): Result<Unit>
    suspend fun getLinkedDevices(): Result<List<LinkedDevice>>
}
