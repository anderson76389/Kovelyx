package com.nuvio.tv.core.sync

import android.content.Context
import android.util.Log
import com.kovelyx.cloud.media.AndroidProfileMediaFiles
import com.kovelyx.cloud.media.ProfileMediaEngine
import com.kovelyx.cloud.media.ProfileMediaRemote
import com.kovelyx.cloud.media.ProfileImageBytes
import com.nuvio.tv.core.auth.CloudflareTvAccountClient
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CloudflareTvProfileMediaSync @Inject constructor(
    @ApplicationContext context: Context,
    private val profiles: ProfileManager
) {
    private val mutex = Mutex()
    private val engine = ProfileMediaEngine(AndroidProfileMediaFiles(context), object : ProfileMediaRemote {
        override suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes): String =
            error("Cette télévision reçoit les images ; modification sur Android mobile ou PC.")
        override suspend fun download(profileId: String, kind: String, version: String) =
            CloudflareTvAccountClient.downloadProfileMedia(profileId, kind, version)
        override suspend fun remove(profileId: String, kind: String, expected: String?): Unit =
            error("Cette télévision ne supprime pas les images du cloud.")
    })

    suspend fun synchronize(reportFailure: Boolean = false) = mutex.withLock {
        val account = CloudflareTvAccountClient.account ?: return@withLock
        val remote = CloudflareTvAccountClient.cloudProfileDetails()
        // Establish stable UUID links before names may change on another device.
        for ((index, row) in remote.withIndex()) {
            check(CloudflareTvAccountClient.account?.id == account.id) { "Compte modifié" }
            val uuid = row.optString("id")
            val name = row.optString("displayName")
            if (!uuid.matches(Regex("[a-fA-F0-9-]{36}")) || name.isBlank()) continue
            val current = profiles.profiles.value
            val linked = current.firstOrNull { it.cloudProfileId == uuid }
            val match = linked ?: current.filter { it.cloudProfileId == null && it.name.equals(name, true) }.singleOrNull()
                ?: current.firstOrNull { index == 0 && it.id == 1 && it.cloudProfileId == null }
            val local = match ?: profiles.createProfile(name, "#1E88E5") ?: continue
            if (local.name != name || local.cloudProfileId != uuid)
                profiles.updateProfile(local.copy(name = name, cloudProfileId = uuid))
        }
        for (local in profiles.profiles.value) {
            val row = (if (local.cloudProfileId != null) remote.firstOrNull { it.optString("id") == local.cloudProfileId }
                else remote.filter { it.optString("displayName").equals(local.name, true) }.singleOrNull())
                ?: continue
            val uuid = row.optString("id")
            try {
                val avatar = engine.synchronize(account.id, uuid, "avatar", local.avatarUrl,
                    row.optString("avatarPath").takeIf { it != "null" && it.isNotBlank() }, receiveOnly = true)
                val background = engine.synchronize(account.id, uuid, "background", local.profileBackgroundUrl,
                    row.optString("backgroundPath").takeIf { it != "null" && it.isNotBlank() }, receiveOnly = true)
                check(CloudflareTvAccountClient.account?.id == account.id) { "Compte modifié" }
                val current = profiles.profiles.value.firstOrNull { it.id == local.id } ?: continue
                val nextAvatar = if (current.avatarUrl == local.avatarUrl) avatar else current.avatarUrl
                val nextBackground = if (current.profileBackgroundUrl == local.profileBackgroundUrl) background else current.profileBackgroundUrl
                if (current.avatarUrl != nextAvatar || current.profileBackgroundUrl != nextBackground || current.cloudProfileId != uuid)
                    profiles.updateProfile(current.copy(avatarUrl = nextAvatar, profileBackgroundUrl = nextBackground, cloudProfileId = uuid))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.w("KovelyxTvMedia", "Images en attente, fichiers locaux conservés", error)
                if (reportFailure) throw error
            }
        }
    }
}
