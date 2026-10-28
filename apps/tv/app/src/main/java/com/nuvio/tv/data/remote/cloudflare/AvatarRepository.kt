package com.nuvio.tv.data.remote.cloudflare

import javax.inject.Inject
import javax.inject.Singleton

/** Historical refresh policy remains testable, even though the old remote avatar catalog is gone. */
private const val AvatarCatalogRefreshIntervalMs = 15 * 60_000L
internal fun isAvatarCatalogRefreshDue(lastRefreshAtMs: Long, nowMs: Long): Boolean =
    lastRefreshAtMs <= 0L || nowMs < lastRefreshAtMs || nowMs - lastRefreshAtMs >= AvatarCatalogRefreshIntervalMs

data class AvatarCatalogItem(
    val id: String,
    val displayName: String,
    val imageUrl: String,
    val category: String,
    val sortOrder: Int,
    val bgColor: String? = null,
    val memberOnly: Boolean = false
)

/** The old third-party avatar storage is not part of Kovelyx accounts. */
@Singleton
class AvatarRepository @Inject constructor() {
    suspend fun getAvatarCatalog(hasMemberAccess: Boolean = false): List<AvatarCatalogItem> = emptyList()
    fun getAvatarImageUrl(avatarId: String, catalog: List<AvatarCatalogItem>): String? =
        catalog.firstOrNull { it.id == avatarId }?.imageUrl
    fun invalidateCache() = Unit
}
