package com.nuvio.app.features.home

import com.nuvio.app.core.cloud.CloudflareAccountClient
import com.nuvio.app.core.cloud.CloudflareCatalogueSync
import com.nuvio.app.core.cloud.CloudflareDataSync
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SyncCatalogItem(
    @SerialName("addon_id") val addonId: String,
    val type: String,
    @SerialName("catalog_id") val catalogId: String,
    val enabled: Boolean = true,
    val order: Int = 0,
    @SerialName("custom_title") val customTitle: String = "",
    @SerialName("is_collection") val isCollection: Boolean = false,
    @SerialName("collection_id") val collectionId: String = "",
    val key: String = "",
)

@Serializable
data class SyncHomeCatalogPayload(
    @SerialName("show_catalog_type") val showCatalogType: Boolean = true,
    @SerialName("hide_unreleased_content") val hideUnreleasedContent: Boolean = false,
    val items: List<SyncCatalogItem> = emptyList(),
)

internal fun mergeHomeCatalogSettingsJson(
    remoteJson: JsonObject?,
    localJson: JsonObject,
): JsonObject = buildJsonObject {
    remoteJson?.forEach { (key, value) -> put(key, value) }
    localJson.forEach { (key, value) -> put(key, value) }
}

object HomeCatalogSettingsSyncService {
    var isSyncingFromRemote: Boolean = false

    suspend fun pullFromServer(profileId: Int) {
        if (CloudflareAccountClient.enabled && ProfileRepository.activeProfileId == profileId)
            CloudflareDataSync.request(profileId)
    }

    fun triggerPush() {
        if (CloudflareAccountClient.enabled && !isSyncingFromRemote)
            CloudflareCatalogueSync.edited("layout")
    }
}
