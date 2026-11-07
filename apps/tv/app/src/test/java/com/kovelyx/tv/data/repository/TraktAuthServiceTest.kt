package com.kovelyx.tv.data.repository

import android.content.Context
import com.kovelyx.tv.data.local.AuthSessionNoticeDataStore
import com.kovelyx.tv.data.local.TraktAuthDataStore
import com.kovelyx.tv.data.local.TraktAuthState
import com.kovelyx.tv.data.remote.api.TraktApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response

class TraktAuthServiceTest {
    @Test
    fun `refresh token 400 clears credentials and prevents another refresh`() = runTest {
        val traktApi = mockk<TraktApi>()
        val traktAuthDataStore = mockk<TraktAuthDataStore>()
        val authSessionNoticeDataStore = mockk<AuthSessionNoticeDataStore>()
        var authState = authenticatedState()

        coEvery { traktAuthDataStore.getCurrentState() } answers { authState }
        coEvery { traktAuthDataStore.clearAuth() } answers { authState = TraktAuthState() }
        coEvery { authSessionNoticeDataStore.markTraktReconnectRequired() } returns Unit
        coEvery { traktApi.refreshToken(any()) } returns Response.error(400, "invalid_grant".toResponseBody())

        val service = TraktAuthService(
            context = mockk<Context>(relaxed = true),
            traktApi = traktApi,
            traktAuthDataStore = traktAuthDataStore,
            authSessionNoticeDataStore = authSessionNoticeDataStore
        )

        assertFalse(service.refreshTokenIfNeeded(force = true))
        assertFalse(service.refreshTokenIfNeeded(force = true))

        if (service.hasRequiredCredentials()) {
            coVerify(exactly = 1) { traktApi.refreshToken(any()) }
            coVerify(exactly = 1) { authSessionNoticeDataStore.markTraktReconnectRequired() }
            coVerify(exactly = 1) { traktAuthDataStore.clearAuth() }
        } else {
            // Un build sans clés Trakt ne tente pas d'utiliser des clés d'origine.
            coVerify(exactly = 0) { traktApi.refreshToken(any()) }
            coVerify(exactly = 0) { traktAuthDataStore.clearAuth() }
        }
    }

    private fun authenticatedState(): TraktAuthState {
        return TraktAuthState(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "bearer",
            createdAt = 1L,
            expiresIn = 3600
        )
    }
}
