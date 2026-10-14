package com.nuvio.app.features.profiles

internal actual object ProfileLocalImages {
    actual val supported: Boolean = false
    actual suspend fun chooseImage(background: Boolean): String? = null
    actual fun isManagedUri(url: String): Boolean = false
    actual fun savedImage(userId: String, profileIndex: Int, background: Boolean): String? = null
    actual fun saveImage(userId: String, profileIndex: Int, background: Boolean, url: String?) = Unit
}
