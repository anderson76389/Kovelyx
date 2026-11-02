package com.kovelyx.debrid

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class AllDebridClientTest {
    @Test fun nestedFilesUnlockAndCredentialValidationUseOfficialContract() = runTest {
        val calls = mutableListOf<String>()
        val client = AllDebridClient(AllDebridTransport { endpoint, key, fields ->
            assertEquals("private-key", key)
            calls += endpoint
            val data = when (endpoint) {
                "user" -> "{\"user\":{\"username\":\"test\"}}"
                "magnet/upload" -> {
                    assertEquals(listOf("magnets[]" to "magnet:?xt=urn:btih:AbC"), fields)
                    "{\"magnets\":[{\"id\":42,\"ready\":true}]}"
                }
                "magnet/files" -> {
                    assertEquals(listOf("id[]" to "42"), fields)
                    "{\"magnets\":[{\"id\":\"42\",\"files\":[{\"n\":\"Season 01\",\"e\":[{\"n\":\"Show.S01E02.mkv\",\"s\":123,\"l\":\"https://alldebrid.com/f/CaseSensitive\"}]}]}]}"
                }
                "link/unlock" -> {
                    assertEquals(listOf("link" to "https://alldebrid.com/f/CaseSensitive"), fields)
                    "{\"link\":\"https://cdn.example/Stream?token=AbC\",\"filename\":\"Show.mkv\",\"filesize\":123}"
                }
                else -> error("Unexpected request")
            }
            DebridHttpReply(200, "{\"status\":\"success\",\"data\":$data}")
        })
        assertTrue(client.validate("private-key"))
        val file = client.files("private-key", "magnet:?xt=urn:btih:AbC").single()
        assertEquals("Season 01/Show.S01E02.mkv", file.path)
        assertEquals("https://cdn.example/Stream?token=AbC", client.unlock("private-key", file).url)
        assertEquals(listOf("user", "magnet/upload", "magnet/files", "link/unlock"), calls)
    }
    @Test fun http200ApiErrorsAreNotAcceptedAsConnected() = runTest {
        val client = AllDebridClient(AllDebridTransport { _, _, _ ->
            DebridHttpReply(200, "{\"status\":\"error\",\"error\":{\"code\":\"AUTH_BAD_APIKEY\"}}")
        })
        assertTrue(runCatching { client.validate("bad-key") }.isFailure)
    }
    @Test fun uncachedMagnetDoesNotProduceAFakePlayableLink() = runTest {
        var requests = 0
        val client = AllDebridClient(AllDebridTransport { _, _, _ ->
            requests++
            DebridHttpReply(200, "{\"status\":\"success\",\"data\":{\"magnets\":[{\"id\":42,\"ready\":false}]}}")
        })
        assertTrue(client.files("key", "magnet:?xt=urn:btih:abc").isEmpty())
        assertEquals(1, requests)
    }
}
