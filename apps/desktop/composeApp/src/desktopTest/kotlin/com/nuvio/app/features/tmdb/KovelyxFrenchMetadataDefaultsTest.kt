package com.nuvio.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KovelyxFrenchMetadataDefaultsTest {
    @Test fun desktopMetadataDefaultsToFrench() {
        assertTrue(TmdbSettings().language.startsWith("fr", ignoreCase = true))
        assertFalse(TmdbSettings().enabled, "An API key is needed before querying TMDB")
    }
}
