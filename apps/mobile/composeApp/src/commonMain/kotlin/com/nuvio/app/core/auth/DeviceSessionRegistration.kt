package com.nuvio.app.core.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val CLIENT_NAME = "Kovelyx Mobile"

internal data class DeviceClientMetadata(
    val deviceName: String,
    val platform: String,
)

internal expect fun currentDeviceClientMetadata(): DeviceClientMetadata

/** Cloudflare Better Auth already owns device sessions; no legacy RPC registration. */
object DeviceSessionRegistration {
    suspend fun registerIfAuthenticated(force: Boolean = false): Boolean =
        AuthRepository.state.value.let { it is AuthState.Authenticated && !it.isAnonymous }
}

internal fun buildDeviceRegistrationParams(
    installationId: String,
    clientVersion: String,
    metadata: DeviceClientMetadata,
): JsonObject = buildJsonObject {
    put("p_installation_id", installationId)
    put("p_client_name", CLIENT_NAME)
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
