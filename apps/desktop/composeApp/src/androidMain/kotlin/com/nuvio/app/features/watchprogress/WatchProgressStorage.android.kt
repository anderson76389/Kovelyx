package com.nuvio.app.features.watchprogress

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.cloud.CloudflareAccountClient

actual object WatchProgressStorage {
    private const val preferencesName = "nuvio_watch_progress"
    private const val payloadKey = "watch_progress_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    private fun scopedKey(profileId: Int): String {
        val auth = AuthRepository.state.value as? AuthState.Authenticated
        val owner = auth?.userId?.takeIf {
            CloudflareAccountClient.enabled && !auth.isAnonymous &&
                CloudflareAccountClient.currentSession?.userId == it
        }
        return accountScopedProgressStorageKey(
            legacyKey = "${payloadKey}_$profileId", profileId = profileId, cloudOwnerId = owner
        )
    }

    actual fun loadPayload(profileId: Int): String? =
        preferences?.getString(scopedKey(profileId), null)

    actual fun savePayload(profileId: Int, payload: String) {
        preferences
            ?.edit()
            ?.putString(scopedKey(profileId), payload)
            ?.apply()
    }
}
