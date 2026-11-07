package com.kovelyx.app.core.storage

import com.kovelyx.app.core.build.AppFeaturePolicy
import com.kovelyx.app.core.sync.SyncManager
import com.kovelyx.app.core.sync.ProfileSettingsSync
import com.kovelyx.app.core.tracking.ensureTrackingProvidersRegistered
import com.kovelyx.app.features.addons.AddonRepository
import com.kovelyx.app.features.catalog.CatalogRepository
import com.kovelyx.app.features.collection.CollectionMobileSettingsRepository
import com.kovelyx.app.features.collection.CollectionRepository
import com.kovelyx.app.features.details.MetaDetailsRepository
import com.kovelyx.app.features.details.MetaScreenSettingsRepository
import com.kovelyx.app.features.home.HomeCatalogSettingsRepository
import com.kovelyx.app.features.home.HomeRepository
import com.kovelyx.app.features.library.LibraryRepository
import com.kovelyx.app.features.membership.MemberAccessRepository
import com.kovelyx.app.features.library.LibraryDisplaySettingsRepository
import com.kovelyx.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.kovelyx.app.features.player.PlayerLaunchStore
import com.kovelyx.app.features.player.PlayerSettingsRepository
import com.kovelyx.app.features.p2p.P2pSettingsRepository
import com.kovelyx.app.features.plugins.PluginRepository
import com.kovelyx.app.features.player.SubtitleRepository
import com.kovelyx.app.features.profiles.ProfileRepository
import com.kovelyx.app.features.profiles.MAX_PROFILES
import com.kovelyx.app.features.search.SearchRepository
import com.kovelyx.app.features.servers.ServerRepository
import com.kovelyx.app.features.settings.ThemeSettingsRepository
import com.kovelyx.app.features.streams.StreamContextStore
import com.kovelyx.app.features.streams.StreamBadgeSettingsRepository
import com.kovelyx.app.features.streams.StreamLaunchStore
import com.kovelyx.app.features.streams.StreamsRepository
import com.kovelyx.app.features.tracking.TrackingProviderRegistry
import com.kovelyx.app.features.tracking.TrackingSettingsRepository
import com.kovelyx.app.core.ui.CardDepthStyleRepository
import com.kovelyx.app.core.ui.PosterCardStyleRepository
import com.kovelyx.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.kovelyx.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.kovelyx.app.features.watchprogress.WatchProgressRepository
import com.kovelyx.app.features.watchprogress.WatchProgressSourceCoordinator
import com.kovelyx.app.features.watched.WatchedRepository

internal object LocalAccountDataCleaner {
    fun wipe() {
        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.removeStoredProfiles(1..MAX_PROFILES)
        SyncManager.cancelAccountSync()
        WatchProgressSourceCoordinator.clearLocalState()
        ProfileSettingsSync.clearAccountState()
        ContinueWatchingEnrichmentCache.clearLocalState()
        WatchProgressRepository.clearLocalState()
        WatchedRepository.clearLocalState()
        LibraryRepository.runAccountStorageWipe {
            wipePlatformStorage()
        }

        ProfileRepository.clearInMemory()
        MemberAccessRepository.clearLocalState()
        AddonRepository.clearLocalState()
        ServerRepository.clearLocalState()
        if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.clearLocalState()
        }
        HomeRepository.clear()
        HomeCatalogSettingsRepository.clearLocalState()
        MetaScreenSettingsRepository.clearLocalState()
        com.kovelyx.app.features.shuffle.EpisodeShuffleRepository.clearLocalState()
        LibraryRepository.clearLocalState()
        LibraryDisplaySettingsRepository.clearLocalState()
        ContinueWatchingPreferencesRepository.clearLocalState()
        EpisodeReleaseNotificationsRepository.clearLocalState()
        CollectionMobileSettingsRepository.clearLocalState()
        CollectionRepository.clearLocalState()
        ThemeSettingsRepository.clearLocalState()
        PosterCardStyleRepository.clearLocalState()
        CardDepthStyleRepository.clearLocalState()
        TrackingProviderRegistry.clearLocalState()
        TrackingSettingsRepository.clearLocalState()
        PlayerSettingsRepository.clearLocalState()
        StreamBadgeSettingsRepository.clearLocalState()
        P2pSettingsRepository.clearLocalState()
        CatalogRepository.clear()
        StreamsRepository.clear()
        MetaDetailsRepository.clear()
        SearchRepository.reset()
        SubtitleRepository.clear()
        PlayerLaunchStore.clear()
        StreamLaunchStore.clear()
        StreamContextStore.clear()
    }

    internal fun wipePlatformStorage(wipeStorage: () -> Unit = PlatformLocalAccountDataCleaner::wipe) {
        try {
            wipeStorage()
        } finally {
            ContinueWatchingEnrichmentCache.clearLocalState()
        }
    }
}

internal expect object PlatformLocalAccountDataCleaner {
    fun wipe()
}
