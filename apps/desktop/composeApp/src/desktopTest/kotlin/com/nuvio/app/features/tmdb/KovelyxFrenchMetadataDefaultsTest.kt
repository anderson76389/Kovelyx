package com.nuvio.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KovelyxFrenchMetadataDefaultsTest {
    @Test fun windowsMetadataStartsInFrench() {
        assertEquals("fr-FR", TmdbSettings().language)
        assertFalse(TmdbSettings().enabled, "TMDB must not make API calls without a configured key")
    }
}
