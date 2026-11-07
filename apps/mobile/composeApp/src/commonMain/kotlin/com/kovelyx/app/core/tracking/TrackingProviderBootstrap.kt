package com.kovelyx.app.core.tracking

import com.kovelyx.app.features.tracking.TrackingProviderRegistry
import com.kovelyx.app.features.mdblist.MdbListTracker
import com.kovelyx.app.features.trakt.TraktAuthRepository
import com.kovelyx.app.features.trakt.TraktScrobbleRepository
import com.kovelyx.app.features.trakt.TraktTrackingLibraryProvider
import com.kovelyx.app.features.trakt.TraktTrackingProgressProvider
import com.kovelyx.app.features.watching.sync.TraktWatchedSyncAdapter

fun ensureTrackingProvidersRegistered() {
    MdbListTracker.register()
    TraktAuthRepository.descriptor
    TraktScrobbleRepository.ensureRegistered()
    TrackingProviderRegistry.registerLibraryProvider(TraktTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(TraktWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(TraktTrackingProgressProvider)
}
