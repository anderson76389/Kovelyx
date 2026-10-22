package com.nuvio.app.features.profiles

import com.nuvio.app.core.cloud.CloudflareAccountClient

import android.content.Context
import android.content.SharedPreferences

actual object ProfileStorage {
    private const val preferencesName = "nuvio_profile_cache"
    private fun payloadKey(): String {
        val owner = CloudflareAccountClient.currentSession?.userId
            ?.takeIf { CloudflareAccountClient.enabled && it.matches(Regex("[a-zA-Z0-9_-]{4,128}")) }
        return if (owner == null) "profile_payload" else "kovelyx_cloud_${owner}_profile_payload"
    }

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(payloadKey(), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(payloadKey(), payload)
            ?.apply()
    }
}