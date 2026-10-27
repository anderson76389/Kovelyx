package com.nuvio.app.core.tracking

import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.mdblist.MdbListTracker
import com.nuvio.app.features.trakt.TraktAuthRepository
import com.nuvio.app.features.trakt.TraktScrobbleRepository
import com.nuvio.app.features.trakt.TraktTrackingLibraryProvider
import com.nuvio.app.features.trakt.TraktTrackingProgressProvider
import com.nuvio.app.features.watching.sync.TraktWatchedSyncAdapter

fun ensureTrackingProvidersRegistered() {
    MdbListTracker.register()
    TraktAuthRepository.descriptor
    TraktScrobbleRepository.ensureRegistered()
    TrackingProviderRegistry.registerLibraryProvider(TraktTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(TraktWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(TraktTrackingProgressProvider)
}
