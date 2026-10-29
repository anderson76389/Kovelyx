package com.kovelyx.cloud.media

import kotlinx.serialization.Serializable

const val MAX_PROFILE_MEDIA_BYTES = 2 * 1024 * 1024

@Serializable
data class ProfileMediaState(val uri: String? = null, val version: String? = null, val pending: Boolean = false, val automatic: Boolean = false)
data class ProfileImageBytes(val mime: String, val bytes: ByteArray)

interface ProfileMediaFiles {
    fun loadState(key: String): ProfileMediaState?
    fun saveState(key: String, state: ProfileMediaState)
    fun compareAndSaveState(key: String, expected: ProfileMediaState?, next: ProfileMediaState): Boolean {
        if (loadState(key) != expected) return false
        saveState(key, next)
        return true
    }
    fun exists(uri: String): Boolean
    fun sha256(bytes: ByteArray): String
    suspend fun encodeLocal(uri: String, background: Boolean): ProfileImageBytes
    suspend fun saveDownloaded(key: String, version: String, image: ProfileImageBytes): String
}

interface ProfileMediaRemote {
    suspend fun upload(profileId: String, kind: String, expected: String?, image: ProfileImageBytes): String
    suspend fun download(profileId: String, kind: String, version: String): ProfileImageBytes
    suspend fun remove(profileId: String, kind: String, expected: String?)
}

/** File paths stay on their own device. Only explicitly edited bytes are uploaded. */
class ProfileMediaEngine(private val files: ProfileMediaFiles, private val remote: ProfileMediaRemote) {
    private fun key(owner: String, profile: String, kind: String) = "$owner.$profile.$kind"
    fun edited(owner: String, profile: String, kind: String, uri: String?) {
        val key = key(owner, profile, kind)
        val old = files.loadState(key)
        if (old != null && old.uri == uri && !old.pending) return
        files.saveState(key, ProfileMediaState(uri, old?.version, pending = true))
    }

    suspend fun synchronize(owner: String, profile: String, kind: String, local: String?, version: String?): String? {
        require(kind == "avatar" || kind == "background")
        val key = key(owner, profile, kind)
        var state = files.loadState(key)
        // One-time migration of an existing personal file only when the cloud has no image.
        // An unmarked old local cache must never overwrite another device's newer photo.
        if (state == null && version == null && local?.startsWith("file:") == true) {
            files.compareAndSaveState(key, null, ProfileMediaState(local, pending = true, automatic = true))
            state = files.loadState(key)
        }
        // If another device won a first-import race, its already established photo wins.
        if (state?.pending == true && state.automatic && version != null) {
            files.compareAndSaveState(key, state, ProfileMediaState(state.uri))
            state = files.loadState(key)
        }
        if (state?.pending == true) {
            val next = if (state.uri == null) {
                if (version != null) remote.remove(profile, kind, version)
                null
            } else {
                val image = files.encodeLocal(state.uri, kind == "background")
                require(image.bytes.size in 1..MAX_PROFILE_MEDIA_BYTES) { "Image trop volumineuse pour le cloud (2 Mio maximum)." }
                remote.upload(profile, kind, version, image)
            }
            // Saving another photo during the request preserves that later pending edit.
            files.compareAndSaveState(key, state, ProfileMediaState(state.uri, next))
            return (files.loadState(key) ?: state).uri
        }
        if (version == null) {
            if (state?.version != null) {
                files.compareAndSaveState(key, state, ProfileMediaState())
                return files.loadState(key)?.uri
            }
            return local
        }
        require(version.matches(Regex("[a-f0-9]{64}"))) { "Version d’image distante invalide" }
        if (state?.version == version && state.uri != null && files.exists(state.uri)) return state.uri
        val captured = state
        val image = remote.download(profile, kind, version)
        require(image.bytes.size in 1..MAX_PROFILE_MEDIA_BYTES && files.sha256(image.bytes) == version) {
            "Image distante incomplète ou empreinte différente"
        }
        val uri = files.saveDownloaded(key, version, image)
        if (!files.compareAndSaveState(key, captured, ProfileMediaState(uri, version)))
            return files.loadState(key)?.uri
        return uri
    }
}
