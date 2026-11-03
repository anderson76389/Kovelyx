package com.nuvio.tv.data.local

import com.nuvio.tv.domain.model.LibrarySourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RetiredTrackingPreferencesTest {
    @Test
    fun `old SIMKL preferences select usable sources after upgrade`() {
        assertEquals(WatchProgressSource.NUVIO_SYNC, WatchProgressSource.fromStorage("SIMKL"))
        assertEquals(LibrarySourceMode.LOCAL, librarySourceModeFromStorage("SIMKL"))
        assertEquals(MoreLikeThisSourcePreference.TMDB, recommendationsSourceFromStorage("SIMKL"))
    }

    @Test
    fun `existing Trakt MDBList and local choices remain intact`() {
        assertEquals(WatchProgressSource.TRAKT, WatchProgressSource.fromStorage("TRAKT"))
        assertEquals(WatchProgressSource.MDBLIST, WatchProgressSource.fromStorage("MDBLIST"))
        assertEquals(LibrarySourceMode.MDBLIST, librarySourceModeFromStorage("MDBLIST"))
        assertEquals(LibrarySourceMode.LOCAL, librarySourceModeFromStorage("LOCAL"))
        assertEquals(MoreLikeThisSourcePreference.TRAKT, recommendationsSourceFromStorage("TRAKT"))
    }

    @Test
    fun `missing or unknown preferences preserve existing defaults`() {
        for (value in listOf(null, "unknown")) {
            assertEquals(WatchProgressSource.TRAKT, WatchProgressSource.fromStorage(value))
            assertEquals(LibrarySourceMode.TRAKT, librarySourceModeFromStorage(value))
            assertEquals(MoreLikeThisSourcePreference.TRAKT, recommendationsSourceFromStorage(value))
        }
    }
}
