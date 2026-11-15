package com.kovelyx.tv.ui.screens.collection

import com.kovelyx.tv.domain.model.Addon
import com.kovelyx.tv.domain.model.AddonCatalogCollectionSource
import com.kovelyx.tv.domain.model.CatalogDescriptor
import com.kovelyx.tv.domain.model.findCollectionCatalog

/** Match only installed, enabled catalogues. Never make a request to a former addon URL. */
internal data class ResolvedCollectionAddonCatalog(
    val addon: Addon,
    val catalog: CatalogDescriptor
)

private val aioMetadataCatalogId =
    Regex("^aiometa-[A-Za-z0-9_-]+\\.(tmdb\\.(?:list\\.[0-9]+|discover\\.[A-Za-z0-9_-]+))$")

internal fun String.aioCollectionSemanticId(): String? =
    aioMetadataCatalogId.matchEntire(substringBefore(","))?.groupValues?.get(1)

/**
 * Backups may reference a previous AIO Metadata instance with a different addon ID
 * and catalog prefix. Resolve only the same semantic TMDB list/discover key,
 * same media type, and a UNIQUE installed catalog. Ambiguous matches fail closed.
 */
internal fun List<Addon>.resolveCollectionAddonCatalog(
    source: AddonCatalogCollectionSource
): ResolvedCollectionAddonCatalog? {
    val active = filter { it.enabled }

    fun exact(addon: Addon): CatalogDescriptor? =
        addon.catalogs.findCollectionCatalog(source.type, source.catalogId)
            ?: addon.catalogs.findCollectionCatalog(source.type, source.catalogId.substringBefore(","))

    active.firstOrNull { it.id == source.addonId }?.let { addon ->
        exact(addon)?.let { return ResolvedCollectionAddonCatalog(addon, it) }
    }

    val exactOther = active.mapNotNull { addon ->
        exact(addon)?.let { ResolvedCollectionAddonCatalog(addon, it) }
    }
    if (exactOther.size == 1) return exactOther.single()
    if (exactOther.isNotEmpty()) return null

    val semantic = source.catalogId.aioCollectionSemanticId() ?: return null
    return active.flatMap { addon ->
        addon.catalogs.filter { candidate ->
            candidate.id.aioCollectionSemanticId() == semantic &&
                addon.catalogs.findCollectionCatalog(source.type, candidate.id) == candidate
        }.map { ResolvedCollectionAddonCatalog(addon, it) }
    }.singleOrNull()
}
