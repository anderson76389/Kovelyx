package com.nuvio.app.features.profiles

/** Optionnel : média privé du profil, jamais un lien R2 public. */
internal expect object ProfileCloudMediaTransfer {
    suspend fun remove(profileId: String, background: Boolean)
    suspend fun upload(profileId: String, mediaUri: String, background: Boolean)
    suspend fun download(profileId: String, background: Boolean, remotePath: String): String?
}
