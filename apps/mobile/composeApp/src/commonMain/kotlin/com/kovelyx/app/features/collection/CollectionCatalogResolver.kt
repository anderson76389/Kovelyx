package com.kovelyx.app.features.collection

import com.kovelyx.app.features.addons.AddonCatalog
import com.kovelyx.app.features.addons.ManagedAddon
import com.kovelyx.app.features.addons.enabledAddons

internal data class ResolvedCollectionCatalog(
    val addon: ManagedAddon,
    val catalog: AddonCatalog,
)

internal fun List<ManagedAddon>.findCollectionCatalog(
    source: CollectionCatalogSource,
): ResolvedCollectionCatalog? {
    val activeAddons = enabledAddons()
    val declaredAddon = activeAddons.firstOrNull { it.manifest?.id == source.addonId }
    val declaredCatalog = declaredAddon?.manifest?.catalogs?.findSourceCatalog(source)
    if (declaredAddon != null && declaredCatalog != null) {
        return ResolvedCollectionCatalog(addon = declaredAddon, catalog = declaredCatalog)
    }

    val exact = activeAddons.firstNotNullOfOrNull { addon ->
        val catalog = addon.manifest?.catalogs?.find {
            it.id == source.catalogId && it.type == source.type
        } ?: return@firstNotNullOfOrNull null
        ResolvedCollectionCatalog(addon = addon, catalog = catalog)
    }
    if (exact != null) return exact
    // AIO Metadata prefixes change between configured instances. Only an
    // unambiguous semantic catalog ID may reconnect an imported collection.
    val semantic = source.catalogId.aioMetadataCatalogKey() ?: return null
    return activeAddons.flatMap { addon ->
        addon.manifest?.catalogs.orEmpty().filter {
            it.type == source.type && it.id.aioMetadataCatalogKey() == semantic
        }.map { ResolvedCollectionCatalog(addon, it) }
    }.singleOrNull()
}

internal fun List<AvailableCatalog>.findAvailableCatalog(
    source: CollectionCatalogSource,
): AvailableCatalog? {
    val declaredCatalogs = filter { it.addonId == source.addonId }
    return declaredCatalogs.findSourceCatalog(source)
        ?: firstOrNull { it.catalogId == source.catalogId && it.type == source.type }
        ?: source.catalogId.aioMetadataCatalogKey()?.let { semantic ->
            filter { it.type == source.type && it.catalogId.aioMetadataCatalogKey() == semantic }.singleOrNull()
        }
}

internal fun String.aioMetadataCatalogKey(): String? =
    Regex("^aiometa-[A-Za-z0-9_-]+\\.(tmdb\\.(?:list\\.[0-9]+|discover\\.[A-Za-z0-9_-]+))$")
        .matchEntire(this)?.groupValues?.get(1)


private fun List<AddonCatalog>.findSourceCatalog(source: CollectionCatalogSource): AddonCatalog? =
    find { it.id == source.catalogId && it.type == source.type }
        ?: find { it.id == source.catalogId.substringBefore(",") && it.type == source.type }

private fun List<AvailableCatalog>.findSourceCatalog(source: CollectionCatalogSource): AvailableCatalog? =
    find { it.catalogId == source.catalogId && it.type == source.type }
        ?: find { it.catalogId == source.catalogId.substringBefore(",") && it.type == source.type }
