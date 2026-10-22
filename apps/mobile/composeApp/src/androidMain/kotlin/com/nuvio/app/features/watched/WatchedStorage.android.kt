package com.nuvio.app.features.watched

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.cloud.CloudflareAccountClient

actual object WatchedStorage {
    private const val preferencesName = "nuvio_watched"
    private fun payloadKey(profileId: Int): String {
        val auth = AuthRepository.state.value as? AuthState.Authenticated
        val owner = auth?.userId?.takeIf {
            CloudflareAccountClient.enabled && !auth.isAnonymous
        }
        return accountScopedWatchedStorageKey(
            legacyKey = "watched_payload_$profileId",
            profileId = profileId,
            cloudOwnerId = owner
        )
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

