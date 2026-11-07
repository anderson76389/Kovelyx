package com.kovelyx.app.features.servers.mediabrowser

import com.kovelyx.app.features.servers.ServerConnection
import com.kovelyx.app.features.servers.ServerException
import com.kovelyx.app.features.servers.ServerFailure
import com.kovelyx.app.features.servers.ServerSession
import com.kovelyx.app.features.servers.emby.EmbyProvider
import com.kovelyx.app.features.servers.jellyfin.JellyfinProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MediaBrowserSignInFlowTest {
    @Test
    fun bothProvidersAuthenticateAndReadLibrariesAfterRelativeProxyRedirect() = runTest {
        for (emby in listOf(false, true)) {
            val api = if (emby) "/emby" else ""
            val requests = mutableListOf<HttpRequestData>()
            val http = HttpClient(MockEngine { request ->
                requests += request
                when (request.url.encodedPath) {
                    "/reverse$api/System/Info/Public" -> respond("", HttpStatusCode.TemporaryRedirect,
                        headersOf("Location", "/actual$api/System/Info/Public"))
                    "/actual$api/System/Info/Public" -> respond(
                        """{"ServerName":"Test","Id":"server","Version":"${if (emby) "4.8.10.0" else "10.10.7"}"}""",
                        headers = headersOf("Content-Type", "application/json"))
                    "/actual$api/Users/AuthenticateByName" -> {
                        assertEquals("""{"Username":"viewer","Pw":"test-password"}""", request.text)
                        assertTrue(request.headers[if (emby) "X-Emby-Authorization" else "Authorization"].orEmpty().contains("Client=\"Kovelyx\""))
                        assertNull(request.headers["X-Emby-Token"])
                        respond("""{"User":{"Id":"user","Name":"viewer"},"AccessToken":"test-token","ServerId":"server"}""",
                            headers = headersOf("Content-Type", "application/json"))
                    }
                    (if (emby) "/actual/emby/Users/user/Views" else "/actual/UserViews") -> {
                        assertEquals("test-token", request.headers["X-Emby-Token"])
                        respond("""{"Items":[{"Id":"movies","Name":"Films","CollectionType":"movies"}]}""",
                            headers = headersOf("Content-Type", "application/json"))
                    }
                    else -> error("Unexpected endpoint")
                }
            }) { followRedirects = false; expectSuccess = false }
            try {
                val provider = if (emby) EmbyProvider(http) else JellyfinProvider(http)
                val signed = provider.signIn("https://media.example.test/reverse$api/web/index.html", "viewer", "test-password")
                assertEquals("https://media.example.test/actual", signed.address)
                val connection = ServerConnection("connection", provider.id, signed.serverName, signed.address,
                    signed.serverId, signed.userId, signed.userName, "credential")
                val libraries = provider.libraries(ServerSession(connection, signed.token))
                assertEquals(listOf("Films"), libraries.map { it.name })
                assertEquals(4, requests.size)
            } finally { http.close() }
        }
    }

    @Test
    fun refusesToSendPasswordToAnotherHostOrToDowngradeHttps() = runTest {
        for (location in listOf("https://other.example.test/System/Info/Public", "http://media.example.test/System/Info/Public")) {
            val requests = mutableListOf<HttpRequestData>()
            val http = HttpClient(MockEngine { request ->
                requests += request
                respond("", HttpStatusCode.TemporaryRedirect, headersOf("Location", location))
            }) { followRedirects = false; expectSuccess = false }
            try {
                assertFailsWith<ServerException> {
                    JellyfinProvider(http).signIn("https://media.example.test", "viewer", "test-password")
                }
                assertEquals(1, requests.size)
                assertTrue(requests.none { it.method.value == "POST" })
            } finally { http.close() }
        }
    }

    @Test
    fun invalidCredentialsRemainAnAuthenticationError() = runTest {
        val http = HttpClient(MockEngine { request ->
            if (request.method.value == "GET") respond("""{"Id":"server","Version":"10.10.7"}""",
                headers = headersOf("Content-Type", "application/json"))
            else respond("{}", HttpStatusCode.Unauthorized, headersOf("Content-Type", "application/json"))
        }) { followRedirects = false; expectSuccess = false }
        try {
            val error = assertFailsWith<ServerException> {
                JellyfinProvider(http).signIn("https://media.example.test", "viewer", "wrong")
            }
            assertEquals(ServerFailure.AUTH_REQUIRED, error.failure)
        } finally { http.close() }
    }
}
