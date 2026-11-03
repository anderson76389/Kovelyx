package com.nuvio.tv.core.sync

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.UserProfile
import io.mockk.*
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudflareTvProfileReceiveTest {
    @Test fun `renamed profiles with duplicate names retain UUIDs and never write images`() = runTest {
        val first = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val second = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        val state = MutableStateFlow(listOf(
            UserProfile(id = 1, name = "Ancien A", avatarColorHex = "#000000", cloudProfileId = first),
            UserProfile(id = 2, name = "Ancien B", avatarColorHex = "#ffffff", cloudProfileId = second)))
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns File(System.getProperty("java.io.tmpdir"), "kovelyx-profile-receive-test")
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        val profiles = mockk<ProfileManager>(relaxed = true)
        every { profiles.profiles } returns state
        coEvery { profiles.updateProfile(any()) } coAnswers {
            val next = firstArg<UserProfile>()
            backgroundScope.launch { delay(1); state.value = state.value.map { if (it.id == next.id) next else it } }
            true
        }
        fun row(uuid: String): JSONObject = mockk<JSONObject>().also {
            every { it.optString("id") } returns uuid
            every { it.optString("displayName") } returns "Famille"
            every { it.optString("avatarPath") } returns "null"
            every { it.optString("backgroundPath") } returns "null"
        }
        mockkObject(CloudflareTvAccountClient)
        try {
            every { CloudflareTvAccountClient.account } returns CloudflareTvAccountClient.Account("owner", "a@example.test", "test-session")
            coEvery { CloudflareTvAccountClient.cloudProfileDetails() } returns listOf(row(first), row(second))
            CloudflareTvProfileMediaSync(context, profiles).synchronize()
            assertEquals(listOf("Famille", "Famille"), state.value.map { it.name })
            assertEquals(listOf(first, second), state.value.map { it.cloudProfileId })
            coVerify(exactly = 0) { CloudflareTvAccountClient.uploadProfileMedia(any(), any(), any(), any()) }
            coVerify(exactly = 0) { CloudflareTvAccountClient.removeProfileMedia(any(), any(), any()) }
        } finally { unmockkObject(CloudflareTvAccountClient) }
    }
}
