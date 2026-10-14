package com.nuvio.app.features.profiles

/**
 * A desktop-only image importer. Imported files are stored under Kovelyx user
 * data and never require their original source file or a Nuvio service.
 * Android/iOS provide inert implementations until their own pickers are built.
 */
internal expect object ProfileLocalImages {
    val supported: Boolean
    suspend fun chooseImage(background: Boolean): String?
    fun isManagedUri(url: String): Boolean
    fun savedImage(userId: String, profileIndex: Int, background: Boolean): String?
    fun saveImage(userId: String, profileIndex: Int, background: Boolean, url: String?)
}
