package com.nuvio.app.features.tracking

import com.nuvio.app.features.library.LibrarySourceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackingSourcesTest {
    @Test
    fun `retired SIMKL preferences migrate without changing supported providers`() {
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage("SIMKL"))
        assertEquals(LibrarySourceMode.LOCAL, librarySourceModeFromStorage("SIMKL"))
        assertEquals(
            com.nuvio.app.features.trakt.MoreLikeThisSourcePreference.TMDB,
            com.nuvio.app.features.trakt.MoreLikeThisSourcePreference.fromStorage("SIMKL"),
        )
        assertEquals(WatchProgressSource.MDBLIST, WatchProgressSource.fromStorage("MDBLIST"))
        assertEquals(LibrarySourceMode.MDBLIST, librarySourceModeFromStorage("MDBLIST"))
    }

    @Test
    fun `stored legacy source names retain their meaning`() {
        assertEquals(WatchProgressSource.TRAKT, WatchProgressSource.fromStorage("TRAKT"))
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage("NUVIO_SYNC"))
        assertEquals(LibrarySourceMode.TRAKT, librarySourceModeFromStorage("TRAKT"))
    }

    @Test
    fun `remote watch source falls back when its provider is disconnected`() {
        assertEquals(
            WatchProgressSource.NUVIO_SYNC,
            effectiveWatchProgressSource(WatchProgressSource.SIMKL) { false },
        )
        assertEquals(
            WatchProgressSource.SIMKL,
            effectiveWatchProgressSource(WatchProgressSource.SIMKL) { it == TrackingProviderId.SIMKL },
        )
    }

    @Test
    fun `remote library source falls back to local when disconnected`() {
        assertEquals(
            LibrarySourceMode.LOCAL,
            effectiveLibrarySourceMode(LibrarySourceMode.SIMKL) { false },
        )
        assertEquals(
            LibrarySourceMode.SIMKL,
            effectiveLibrarySourceMode(LibrarySourceMode.SIMKL) { it == TrackingProviderId.SIMKL },
        )
    }

    @Test
    fun `local sources have no remote provider`() {
        assertNull(WatchProgressSource.NUVIO_SYNC.providerId)
        assertNull(LibrarySourceMode.LOCAL.providerId)
    }
}
