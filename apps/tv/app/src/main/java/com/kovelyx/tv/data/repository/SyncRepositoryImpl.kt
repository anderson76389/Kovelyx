package com.kovelyx.tv.data.repository

import com.kovelyx.tv.data.remote.cloud.ClaimSyncResult
import com.kovelyx.tv.data.remote.cloud.LinkedDevice
import com.kovelyx.tv.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The old device-code protocol is unavailable: it may never fall back to a
 * third-party server. A future dedicated Workers pairing endpoint is required.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor() : SyncRepository {
    private fun unavailable(): UnsupportedOperationException =
        UnsupportedOperationException("Liaison d'appareil Kovelyx Cloudflare non disponible")

    override suspend fun generateSyncCode(pin: String): Result<String> = Result.failure(unavailable())
    override suspend fun getSyncCode(pin: String): Result<String> = Result.failure(unavailable())
    override suspend fun claimSyncCode(
        code: String, pin: String, deviceName: String?
    ): Result<ClaimSyncResult> = Result.failure(unavailable())
    override suspend fun unlinkDevice(deviceUserId: String): Result<Unit> = Result.failure(unavailable())
    override suspend fun getLinkedDevices(): Result<List<LinkedDevice>> =
        Result.failure(unavailable())
}
