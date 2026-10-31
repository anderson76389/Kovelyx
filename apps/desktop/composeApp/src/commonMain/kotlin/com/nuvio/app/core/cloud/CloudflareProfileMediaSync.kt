package com.nuvio.app.core.cloud

import co.touchlab.kermit.Logger
import com.kovelyx.cloud.media.ProfileMediaEngine
import com.kovelyx.cloud.media.ProfileMediaRemote
import com.kovelyx.cloud.media.ProfileImageBytes
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.profiles.NuvioProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object CloudflareProfileMediaSync {
    private val mutex = Mutex()
    private val log = Logger.withTag("KovelyxProfileMedia")
    private var engine: ProfileMediaEngine? = null
    private fun engine(): ProfileMediaEngine? {
        engine?.let { return it }
        val files = CloudflareProfileMediaPlatform.files ?: return null
        return ProfileMediaEngine(files, object : ProfileMediaRemote {
            override suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes) =
                CloudflareAccountClient.uploadProfileMedia(profileId, kind, expected, image)
            override suspend fun download(profileId: String, kind: String, version: String) =
                CloudflareAccountClient.downloadProfileMedia(profileId, kind, version)
            override suspend fun remove(profileId: String, kind: String, expected: String?) =
                CloudflareAccountClient.removeProfileMedia(profileId, kind, expected)
        }).also { engine = it }
    }
    fun edited(owner: String, profile: String, kind: String, uri: String?) {
        engine()?.edited(owner, profile, kind, uri?.takeIf { it.startsWith("file:") })
    }
    suspend fun synchronize(profiles: List<NuvioProfile>, reportFailure: Boolean = false): List<NuvioProfile> = mutex.withLock {
        val account = AuthRepository.state.value as? AuthState.Authenticated ?: return@withLock profiles
        val engine = engine() ?: return@withLock profiles
        val remote = CloudflareAccountClient.accountProfiles()["profiles"]?.jsonArray.orEmpty()
            .associate { val row = it.jsonObject; row["id"]?.jsonPrimitive?.content.orEmpty() to row }
        profiles.map { local ->
            val row = remote[local.id] ?: return@map local
            try {
                val avatar = engine.synchronize(account.userId, local.id, "avatar", local.avatarUrl,
                    row["avatarPath"]?.jsonPrimitive?.content?.takeIf { it != "null" && it.isNotBlank() })
                val background = engine.synchronize(account.userId, local.id, "background", local.profileBackgroundUrl,
                    row["backgroundPath"]?.jsonPrimitive?.content?.takeIf { it != "null" && it.isNotBlank() })
                check((AuthRepository.state.value as? AuthState.Authenticated)?.userId == account.userId) { "Compte modifié" }
                local.copy(avatarUrl = avatar, profileBackgroundUrl = background)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                log.w(error) { "Images privées en attente : copie locale conservée" }
                if (reportFailure) throw error
                local
            }
        }
    }
}
