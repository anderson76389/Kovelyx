package com.kovelyx.app.features.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Profile pictures are now managed by CloudflareProfileMediaSync. The retired
 * third-party avatar catalogue and storage bucket are intentionally absent.
 */
internal fun availableAvatarCatalog(
    standardCatalog: List<AvatarCatalogItem>,
    memberCatalog: List<AvatarCatalogItem>,
    hasMemberAccess: Boolean,
): List<AvatarCatalogItem> = standardCatalog + if (hasMemberAccess) memberCatalog else emptyList()

object AvatarRepository {
    private val _avatars = MutableStateFlow<List<AvatarCatalogItem>>(emptyList())
    val avatars: StateFlow<List<AvatarCatalogItem>> = _avatars.asStateFlow()

    suspend fun fetchAvatars() { _avatars.value = emptyList() }
    suspend fun refreshAvatars(force: Boolean = false) { _avatars.value = emptyList() }
}
