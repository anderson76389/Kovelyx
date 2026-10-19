package com.nuvio.app.features.library

import com.nuvio.app.core.cloud.CloudflareAccountClient

import android.content.Context
import android.content.SharedPreferences

actual object LibraryStorage {
    private const val preferencesName = "nuvio_library"
    private fun payloadKey(profileId: Int): String {
        val owner = CloudflareAccountClient.currentSession?.userId
            ?.takeIf { CloudflareAccountClient.enabled && it.matches(Regex("[a-zA-Z0-9_-]{4,128}")) }
        return if (owner == null) "library_payload_$profileId"
            else "kovelyx_cloud_${owner}_library_payload_$profileId"
    }

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(profileId: Int): String? =
        preferences?.getString(payloadKey(profileId), null)

    actual fun savePayload(profileId: Int, payload: String) {
        preferences
            ?.edit()
            ?.putString(payloadKey(profileId), payload)
            ?.apply()
    }
}
