package com.nuvio.app.core.auth

import com.nuvio.app.core.cloud.CloudflareAccountClient
import com.nuvio.app.core.sync.SyncClientIdentity
import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class DeviceClientMetadata(val clientName: String, val deviceName: String, val platform: String)
internal expect fun currentDeviceClientMetadata(): DeviceClientMetadata

/** No device registration RPC is necessary to use an authenticated D1 profile. */
object DeviceSessionRegistration {
    suspend fun registerIfAuthenticated(force: Boolean = false): Boolean {
        val current = AuthRepository.state.value as? AuthState.Authenticated ?: return false
        return !current.isAnonymous &&
            CloudflareAccountClient.currentSession?.userId == current.userId
    }
}

internal fun buildDeviceRegistrationParams(
    installationId: String,
    clientVersion: String,
    metadata: DeviceClientMetadata,
): JsonObject = buildJsonObject {
    put("p_installation_id", installationId)
    put("p_client_name", metadata.clientName)
    put("p_client_version", clientVersion)
    put("p_platform", metadata.platform)
    put("p_device_name", metadata.deviceName)
}

internal fun formatDeviceName(
    manufacturer: String,
    model: String,
    fallback: String,
): String {
    val normalizedManufacturer = manufacturer.trim()
    val normalizedModel = model.trim()

    return when {
        normalizedModel.isBlank() -> fallback
        normalizedManufacturer.isBlank() -> normalizedModel
        normalizedModel.startsWith(normalizedManufacturer, ignoreCase = true) -> normalizedModel
        else -> "$normalizedManufacturer $normalizedModel"
    }
}
