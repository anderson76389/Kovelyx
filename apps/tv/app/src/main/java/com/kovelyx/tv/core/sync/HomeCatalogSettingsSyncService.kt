package com.kovelyx.tv.core.sync

import com.kovelyx.tv.core.auth.CloudflareTvAccountClient
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
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
)

@Serializable
data class SyncHomeCatalogPayload(
    @SerialName("hide_unreleased_content") val hideUnreleasedContent: Boolean = false,
    val items: List<SyncCatalogItem> = emptyList(),
)


internal fun mergeHomeCatalogSettingsJson(
    remoteJson: JsonObject?,
    localJson: JsonObject
): JsonObject = buildJsonObject {
    remoteJson?.forEach { (key, value) -> put(key, value) }
    localJson.forEach { (key, value) -> put(key, value) }
}


@Singleton
class HomeCatalogSettingsSyncService @Inject constructor(
    private val catalogues: Lazy<CloudflareTvCatalogueSync>
) {
    @Volatile var isSyncingFromRemote: Boolean = false

    suspend fun pushToRemote(reason: String = "unspecified"): Result<Unit> = runCatching {
        if (CloudflareTvAccountClient.enabled) catalogues.get().edited("layout")
    }

    suspend fun pullFromRemote(): Result<Boolean> = runCatching {
        if (CloudflareTvAccountClient.enabled) catalogues.get().request()
        false
    }

    fun triggerPush() {
        if (!isSyncingFromRemote && CloudflareTvAccountClient.enabled)
            catalogues.get().edited("layout")
    }
}
