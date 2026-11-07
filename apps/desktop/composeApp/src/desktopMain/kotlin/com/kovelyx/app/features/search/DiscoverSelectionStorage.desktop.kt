package com.kovelyx.app.features.search

import com.kovelyx.app.core.storage.DesktopStorage
import com.kovelyx.app.core.storage.ProfileScopedKey

internal actual object DiscoverSelectionStorage {
    private val store = DesktopStorage.store("nuvio_discover_selection")

    actual fun loadCatalogKey(): String? =
        store.getString(ProfileScopedKey.of("discover_catalog_key"))

    actual fun saveCatalogKey(catalogKey: String) {
        store.putString(ProfileScopedKey.of("discover_catalog_key"), catalogKey)
    }
}
