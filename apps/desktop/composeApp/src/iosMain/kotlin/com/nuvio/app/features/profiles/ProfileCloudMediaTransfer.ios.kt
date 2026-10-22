package com.nuvio.app.features.profiles

internal actual object ProfileCloudMediaTransfer {
    actual suspend fun remove(profileId: String, background: Boolean) = Unit
    actual suspend fun upload(profileId: String, mediaUri: String, background: Boolean) = Unit
    actual suspend fun download(profileId: String, background: Boolean, remotePath: String): String? = null
}
