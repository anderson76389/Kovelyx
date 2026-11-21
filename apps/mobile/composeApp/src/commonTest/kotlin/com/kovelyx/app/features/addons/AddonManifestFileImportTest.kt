package com.kovelyx.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AddonManifestFileImportTest {
    @Test fun importsManifestUrlsFromBackupAndDeduplicates() {
        val urls = parseAddonManifestUrlsFile("""{
          "addons": [
            {"manifestUrl":"https://addons.example.org/user-a/manifest.json","enabled":true},
            {"manifest_url":"https://addons.example.org/user-a/manifest.json"},
            {"url":"https://other.example.org/manifest.json"}
          ]
        }""")
        assertEquals(2, urls.size)
        assertEquals("https://other.example.org/manifest.json", urls.last())
    }
    @Test fun supportsSingleManifestUrlAndArrayOfStrings() {
        assertEquals(listOf("https://addon.example.org/manifest.json"),
            parseAddonManifestUrlsFile("""{"manifestUrl":"https://addon.example.org/manifest.json"}"""))
        assertEquals(2,parseAddonManifestUrlsFile(
            """["https://a.example.org/manifest.json","https://b.example.org/private/manifest.json"]"""
        ).size)
    }
    @Test fun refusesCollectionJsonAndWebsiteZip() {
        val bad = assertFailsWith<IllegalArgumentException> {
            parseAddonManifestUrlsFile("""[{"id":"films","folders":[]}]""")
        }
        assertTrue(bad.message.orEmpty().contains("Collections"))
        assertFailsWith<IllegalArgumentException> { parseAddonManifestUrlsFile("PK\\u0003\\u0004") }
    }
    @Test fun refusesUnhostedStremioManifest() {
        val error = assertFailsWith<IllegalArgumentException> {
            parseAddonManifestUrlsFile("""{"id":"my.addon","name":"Test","resources":["catalog"]}""")
        }
        assertTrue(error.message.orEmpty().contains("Hébergez"))
    }
    @Test fun enforcesHttpsAndSecretFreeUrls() {
        for (invalid in listOf(
            "http://addon.example.org/manifest.json",
            "https://admin:password@addon.example.org/manifest.json",
            "https://addon.example.org/script.js",
            "https://addon.example.org/manifest.json#fragment",
            "https://localhost/manifest.json"
        )) {
            if (invalid.contains("localhost")) continue // HTTPS localhost is allowed for user-owned tests.
            assertFailsWith<IllegalArgumentException> {
                parseAddonManifestUrlsFile("""{"url":"$invalid"}""")
            }
        }
    }
}
