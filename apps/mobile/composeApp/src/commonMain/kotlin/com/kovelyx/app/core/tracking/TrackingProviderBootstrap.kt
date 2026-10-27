package com.kovelyx.app.core.tracking

import com.kovelyx.app.features.simkl.SimklAuthRepository
import com.kovelyx.app.features.simkl.SimklMutationRepository
import com.kovelyx.app.features.simkl.SimklLibraryRepository
import com.kovelyx.app.features.simkl.SimklProgressRepository
import com.kovelyx.app.features.simkl.SimklTrackingLibraryProvider
import com.kovelyx.app.features.simkl.SimklTrackingProgressProvider
import com.kovelyx.app.features.simkl.SimklWatchedSyncAdapter
import com.kovelyx.app.features.simkl.SimklSyncRepository
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
    SimklAuthRepository.descriptor
    SimklSyncRepository.state
    SimklLibraryRepository.uiState
    SimklProgressRepository.uiState
    SimklMutationRepository.ensureRegistered()
    TrackingProviderRegistry.registerLibraryProvider(TraktTrackingLibraryProvider)
    TrackingProviderRegistry.registerLibraryProvider(SimklTrackingLibraryProvider)
    TrackingProviderRegistry.registerWatchedProvider(TraktWatchedSyncAdapter)
    TrackingProviderRegistry.registerWatchedProvider(SimklWatchedSyncAdapter)
    TrackingProviderRegistry.registerProgressProvider(TraktTrackingProgressProvider)
    TrackingProviderRegistry.registerProgressProvider(SimklTrackingProgressProvider)
}
