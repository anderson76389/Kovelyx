package com.kovelyx.cloud.preferences

import kotlin.test.*

class PluginRepositoryUrlsTest {
    @Test fun configuredPathAndQueryKeepTheirCaseAndTrailingSlash() {
        assertEquals("https://plugins.example/Config/AbC/manifest.json?token=AbC%2FZ&path=/",
            canonicalPluginRepositoryUrl("HTTPS://PLUGINS.EXAMPLE/Config/AbC/?token=AbC%2FZ&path=/#ignored"))
        assertNotEquals(canonicalPluginRepositoryUrl("https://plugins.example/AbC"), canonicalPluginRepositoryUrl("https://plugins.example/abc"))
    }
    @Test fun tvExternalJsonDoesNotReceiveAManifestSuffix() {
        assertEquals("https://plugins.example/Repo.json?key=AbC/", canonicalPluginRepositoryUrl("https://plugins.example/Repo.json?key=AbC/", true))
    }
    @Test fun relativeCodeKeepsConfiguredQueryAndAbsoluteCodeKeepsItsOwnOrigin() {
        val manifest = "https://plugins.example/Config/manifest.json?key=AbC%2F&path=/"
        assertEquals("https://plugins.example/Config/Code.js?key=AbC%2F&path=/", pluginCodeUrl(manifest, "Code.js"))
        assertEquals("https://plugins.example/Config/Code.js?other=Z/", pluginCodeUrl(manifest, "Code.js?other=Z/"))
        assertEquals("https://cdn.example/Code.js", pluginCodeUrl(manifest, "https://cdn.example/Code.js"))
    }
}
