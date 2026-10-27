package com.nuvio.app.features.plugins

import com.nuvio.app.features.plugins.runtime.PluginRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginRuntimeDesktopTest {
    @Test
    fun `desktop runtime executes scraper code`() = runBlocking {
        val results = PluginRuntime.executePlugin(
            code = """
                module.exports.getStreams = async function(tmdbId, mediaType) {
                    return [{
                        title: "Desktop stream " + tmdbId + " " + mediaType,
                        url: "https://example.test/movie.mp4",
                        quality: "1080p",
                        provider: "Desktop Test"
                    }];
                };
            """.trimIndent(),
            tmdbId = "603",
            mediaType = "movie",
            season = null,
            episode = null,
            scraperId = "desktop-runtime-test",
        )

        assertEquals(1, results.size)
        assertEquals("Desktop stream 603 movie", results.single().title)
        assertEquals("https://example.test/movie.mp4", results.single().url)
        assertEquals("1080p", results.single().quality)
        assertEquals("Desktop Test", results.single().provider)
    }

    @Test
    fun `desktop runtime handles concurrent scraper executions`() = runBlocking {
        // Chaque instance QuickJS démarre son propre moteur natif. Sur les
        // runners Windows partagés, huit lancements simultanés ont déjà saturé
        // le système et déclenché le timeout réel de 60 s d'un scraper.
        // Tester plusieurs exécutions en parallèle, mais par vagues bornées
        // à trois, évite de confondre contention du CI et échec fonctionnel.
        val results = (0 until 12).chunked(3).flatMap { batch ->
            coroutineScope {
                batch.map { index ->
                    async(Dispatchers.Default) {
                        PluginRuntime.executePlugin(
                        code = """
                            module.exports.getStreams = async function(tmdbId, mediaType) {
                                await Promise.resolve();
                                return [{
                                    title: "Concurrent stream " + tmdbId + " " + mediaType,
                                    url: "https://example.test/" + tmdbId + ".mp4",
                                    provider: "Desktop Stress Test"
                                }];
                            };
                        """.trimIndent(),
                        tmdbId = index.toString(),
                        mediaType = "movie",
                        season = null,
                        episode = null,
                        scraperId = "desktop-runtime-stress-$index",
                        )
                    }
                }.awaitAll()
            }
        }

        assertEquals(12, results.size)
        results.forEachIndexed { index, streams ->
            assertEquals(1, streams.size)
            assertEquals("https://example.test/$index.mp4", streams.single().url)
        }
    }
}
