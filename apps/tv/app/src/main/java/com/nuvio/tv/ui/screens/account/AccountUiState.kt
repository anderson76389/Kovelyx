package com.nuvio.tv.ui.screens.account

import android.graphics.Bitmap
import com.nuvio.tv.data.remote.supabase.SupabaseLinkedDevice
import com.nuvio.tv.domain.model.AuthState

data class AccountConnectedStats(
    val addons: Int = 0,
    val plugins: Int = 0,
    val library: Int = 0,
    val watchProgress: Int = 0
)

data class ProfileSyncStats(
    val profileId: Int,
    val profileName: String,
    val avatarColorHex: String,
    val addons: Int = 0,
    val plugins: Int = 0,
    val library: Int = 0,
    val watchProgress: Int = 0,
    val watchedItems: Int = 0
)

data class SyncOverview(
    val profileCount: Int = 0,
    val totalAddons: Int = 0,
    val totalPlugins: Int = 0,
    val totalLibrary: Int = 0,
    val totalWatchProgress: Int = 0,
    val totalWatchedItems: Int = 0,
    val perProfile: List<ProfileSyncStats> = emptyList()
)

data class CloudProfileSyncPreview(
    val name: String,
    val libraryItems: Int,
    val progressItems: Int
)

data class AccountUiState(
    val authState: AuthState = AuthState.Loading,
    val isLoading: Boolean = false,
    val isStatsLoading: Boolean = false,
    val error: String? = null,
    val generatedSyncCode: String? = null,
    val syncClaimSuccess: Boolean = false,
    val linkedDevices: List<SupabaseLinkedDevice> = emptyList(),
    // Liste distante uniquement : aucun profil local remplacé automatiquement.
    val cloudProfileNames: List<String> = emptyList(),
    // Lecture D1 seulement : ne reflète pas encore la progression du lecteur TV.
    val cloudSyncPreview: List<CloudProfileSyncPreview> = emptyList(),
    val cloudPreviewLoading: Boolean = false,
    val cloudPreviewError: Boolean = false,
    val effectiveOwnerId: String? = null,
    val connectedStats: AccountConnectedStats? = null,
    val syncOverview: SyncOverview? = null,
    val isSyncOverviewLoading: Boolean = false,
    val qrLoginCode: String? = null,
    val qrLoginUserCode: String? = null,
    val qrLoginUrl: String? = null,
    val qrLoginVerificationUri: String? = null,
    val qrLoginNonce: String? = null,
    val qrLoginBitmap: Bitmap? = null,
    val qrLoginStatus: String? = null,
    val qrLoginExpiresAtMillis: Long? = null,
    val qrLoginPollIntervalSeconds: Int = 3
)
