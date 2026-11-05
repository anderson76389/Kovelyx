package com.nuvio.tv.core.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nuvio.tv.domain.model.AuthState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val CLIENT_NAME = "Kovelyx TV"
private const val MAX_DEVICE_NAME_LENGTH = 160

internal data class DeviceClientMetadata(
    val deviceName: String,
    val platform: String
)

@Singleton
class DeviceSessionRegistration @Inject constructor(private val authManager: AuthManager) {
    fun requestForegroundRegistration() = Unit
    internal suspend fun registerIfAuthenticated(force: Boolean = false): Boolean =
        authManager.authState.value is AuthState.FullAccount
}

internal fun buildDeviceRegistrationParams(
    installationId: String,
    clientVersion: String,
    metadata: DeviceClientMetadata
): JsonObject = buildJsonObject {
    put("p_installation_id", installationId)
    put("p_client_name", CLIENT_NAME)
    put("p_client_version", clientVersion)
    put("p_platform", metadata.platform)
    put("p_device_name", metadata.deviceName)
}

internal fun currentDeviceClientMetadata(context: Context): DeviceClientMetadata {
    val configuredName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
    } else {
        null
    }
    val deviceName = selectDeviceName(
        configuredName = configuredName,
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        fallback = "Android TV"
    ).take(MAX_DEVICE_NAME_LENGTH)
    val osVersion = Build.VERSION.RELEASE.orEmpty()
        .trim()
        .ifBlank { Build.VERSION.SDK_INT.toString() }

    return DeviceClientMetadata(
        deviceName = deviceName,
        platform = "Android TV $osVersion"
    )
}

internal fun selectDeviceName(
    configuredName: String?,
    manufacturer: String,
    model: String,
    fallback: String
): String {
    return configuredName
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: formatDeviceName(manufacturer, model, fallback)
}

internal fun formatDeviceName(
    manufacturer: String,
    model: String,
    fallback: String
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
