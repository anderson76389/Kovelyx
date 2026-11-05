package com.nuvio.tv.core.sync

import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/** Collections are synchronized through Kovelyx Workers/D1 only. */
@Singleton
class CollectionSyncService @Inject constructor(
    private val catalogues: Lazy<CloudflareTvCatalogueSync>
) {
    @Volatile var isSyncingFromRemote: Boolean = false

    suspend fun pushToRemote(): Result<Unit> = runCatching {
        if (CloudflareTvAccountClient.enabled) catalogues.get().edited("collections")
    }

    suspend fun pullFromRemote(): Result<Boolean> = runCatching {
        if (CloudflareTvAccountClient.enabled) catalogues.get().request()
        false
    }

    fun triggerPush() {
        if (!isSyncingFromRemote && CloudflareTvAccountClient.enabled)
            catalogues.get().edited("collections")
    }
}
