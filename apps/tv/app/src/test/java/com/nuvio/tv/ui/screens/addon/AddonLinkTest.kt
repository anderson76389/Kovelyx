package com.nuvio.tv.ui.screens.addon

import org.junit.Assert.assertEquals
import org.junit.Test

class AddonLinkTest {
    @Test fun configuredLinkKeepsItsExactPathAndQuery() {
        assertEquals("https://example.test/CaseSensitive%2Fconfig/manifest.json?token=Ab%2FC&path=/",
            addonManifestLink("https://example.test/CaseSensitive%2Fconfig/?token=Ab%2FC&path=/"))
    }

    @Test fun manifestSuffixIsNotDuplicated() {
        assertEquals("https://example.test/config/manifest.json?key=Value",
            addonManifestLink("https://example.test/config/manifest.json?key=Value"))
    }
}
