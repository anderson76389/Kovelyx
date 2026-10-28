package com.nuvio.tv.data.remote.cloudflare

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileBackgroundCatalogItem(
    val id: String,
    val displayName: String,
    val imageFile: File? = null,
    val assetVersion: Int
)

/** No former third-party background storage: user-imported profile media is handled separately. */
@Singleton
class ProfileBackgroundRepository @Inject constructor() {
    private val _catalog = MutableStateFlow<List<ProfileBackgroundCatalogItem>>(emptyList())
    val catalog: StateFlow<List<ProfileBackgroundCatalogItem>> = _catalog.asStateFlow()
    fun ensureLoaded() = Unit
    fun loadSelectedAndPreload(id: String) = Unit
    fun preloadImages() = Unit
    fun invalidateCache() { _catalog.value = emptyList() }
}
