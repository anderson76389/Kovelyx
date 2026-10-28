package com.nuvio.app.features.membership

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileBackgroundCatalogItem(
    val id: String,
    val displayName: String,
    val landscapeImageBytes: ByteArray? = null,
    val portraitImageBytes: ByteArray? = null,
    val assetVersion: Int,
)

/** User-imported backgrounds are managed locally. No old asset-bucket calls. */
object ProfileBackgroundRepository {
    private val _catalog = MutableStateFlow<List<ProfileBackgroundCatalogItem>>(emptyList())
    val catalog: StateFlow<List<ProfileBackgroundCatalogItem>> = _catalog.asStateFlow()

    fun ensureLoaded() = Unit
    fun loadSelectedAndPreload(id: String, portrait: Boolean) = Unit
    fun preloadLandscapeImages() = Unit
    fun invalidate() { _catalog.value = emptyList() }
}
