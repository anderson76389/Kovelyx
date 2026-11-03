package com.nuvio.app.features.debrid

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamClientResolve
import com.russhwolf.settings.SettingsInitializer
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AllDebridHttpTest {
    @Test fun validatesBearerKeyAndResolvesTheRequestedEpisodeThroughRealHttpRequests() = runBlocking {
        SettingsInitializer().create(RuntimeEnvironment.getApplication())
        val server = MockWebServer()
        server.start()
        try {
            fun reply(data: String) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"status\":\"success\",\"data\":$data}")) }
            reply("{\"user\":{\"username\":\"test\"}}")
            reply("{\"magnets\":[{\"id\":42,\"ready\":true}]}")
            reply("{\"magnets\":[{\"id\":42,\"files\":[{\"n\":\"Show.S01E01.mkv\",\"s\":500,\"l\":\"https://alldebrid.com/f/one\"},{\"n\":\"Show.S01E02.mkv\",\"s\":100,\"l\":\"https://alldebrid.com/f/Two\"}]}]}")
            reply("{\"link\":\"https://cdn.example/Video?token=AbC\"}")
            val api = AllDebridProviderApi(server.url("/v4/").toString())
            assertTrue(api.validateApiKey("personal-key"))
            val result = api.resolveClientStream(StreamItem(addonName = "Test", addonId = "test",
                clientResolve = StreamClientResolve(service = "alldebrid", magnetUri = "magnet:?xt=urn:btih:AbC")), "personal-key", 1, 2)
            assertTrue(result is DirectDebridResolveResult.Success)
            assertEquals("https://cdn.example/Video?token=AbC", (result as DirectDebridResolveResult.Success).url)
            val requests = (1..4).map { requireNotNull(server.takeRequest(3, TimeUnit.SECONDS)) }
            assertEquals(listOf("/v4/user", "/v4/magnet/upload", "/v4/magnet/files", "/v4/link/unlock"), requests.map { it.path })
            requests.forEach { assertEquals("Bearer personal-key", it.getHeader("Authorization")); assertFalse(it.path.orEmpty().contains("personal-key")) }
            assertEquals("link=https%3A%2F%2Falldebrid.com%2Ff%2FTwo", requests.last().body.readUtf8())
        } finally { server.shutdown() }
    }
}
