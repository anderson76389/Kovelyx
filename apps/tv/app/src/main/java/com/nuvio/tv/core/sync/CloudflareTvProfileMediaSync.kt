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
        override suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes) =
            CloudflareTvAccountClient.uploadProfileMedia(profileId, kind, expected, image)
        override suspend fun download(profileId: String, kind: String, version: String) =
            CloudflareTvAccountClient.downloadProfileMedia(profileId, kind, version)
        override suspend fun remove(profileId: String, kind: String, expected: String?) =
            CloudflareTvAccountClient.removeProfileMedia(profileId, kind, expected)
    })

    fun markEdited(profile: UserProfile, previous: UserProfile?) {
        val account = CloudflareTvAccountClient.account ?: return
        val uuid = profile.cloudProfileId ?: return
        if (previous?.avatarUrl != profile.avatarUrl)
            engine.edited(account.id, uuid, "avatar", profile.avatarUrl?.takeIf { it.startsWith("file:") })
        if (previous?.profileBackgroundUrl != profile.profileBackgroundUrl)
            engine.edited(account.id, uuid, "background", profile.profileBackgroundUrl?.takeIf { it.startsWith("file:") })
    }

    suspend fun publishEdit(profile: UserProfile, previous: UserProfile?) {
        val account = checkNotNull(CloudflareTvAccountClient.account)
        val remote = CloudflareTvAccountClient.cloudProfileDetails()
        val linked = profile.cloudProfileId
        val matched = if (linked != null) remote.firstOrNull { it.optString("id") == linked }
            ?: error("Le profil lié ne fait pas partie de ce compte")
        else remote.filter { it.optString("displayName").equals(profile.name, true) }.singleOrNull()
        val uuid = matched?.optString("id") ?: CloudflareTvAccountClient.createCloudProfile(profile.name)
        check(CloudflareTvAccountClient.account?.id == account.id)
        profiles.linkCloudProfile(profile.id, uuid)
        if (matched != null && matched.optString("displayName") != profile.name)
            CloudflareTvAccountClient.renameCloudProfile(uuid, profile.name)
        if (previous?.avatarUrl != profile.avatarUrl)
            engine.edited(account.id, uuid, "avatar", profile.avatarUrl?.takeIf { it.startsWith("file:") })
        if (previous?.profileBackgroundUrl != profile.profileBackgroundUrl)
            engine.edited(account.id, uuid, "background", profile.profileBackgroundUrl?.takeIf { it.startsWith("file:") })
        synchronize(reportFailure = true)
    }

    suspend fun synchronize(reportFailure: Boolean = false) = mutex.withLock {
        val account = CloudflareTvAccountClient.account ?: return@withLock
        val remote = CloudflareTvAccountClient.cloudProfileDetails()
        for (local in profiles.profiles.value) {
            val row = (if (local.cloudProfileId != null) remote.firstOrNull { it.optString("id") == local.cloudProfileId }
                else remote.filter { it.optString("displayName").equals(local.name, true) }.singleOrNull())
                ?: continue
            val uuid = row.optString("id")
            try {
                val avatar = engine.synchronize(account.id, uuid, "avatar", local.avatarUrl,
                    row.optString("avatarPath").takeIf { it != "null" && it.isNotBlank() })
                val background = engine.synchronize(account.id, uuid, "background", local.profileBackgroundUrl,
                    row.optString("backgroundPath").takeIf { it != "null" && it.isNotBlank() })
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
