package com.nuvio.app.core.sync

import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridSettings
import com.nuvio.app.features.mdblist.MdbListSettings
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.tmdb.TmdbSettings

/**
 * Secrets are device-local until the client has genuine end-to-end encryption.
 * The D1 Worker rejects API keys and bearer tokens; do not call the removed
 * SQL RPC, report fake successful uploads, or delete a user's local credentials.
 */
object ProviderCredentialSync {
    fun startObserving() = Unit
    fun clearAccountState() = Unit
    internal fun onProfileChanged() = Unit

    suspend fun syncFromRemote(profileId: Int): Boolean = false

    internal fun buildSnapshot(
        profileId: Int,
        debrid: DebridSettings,
        tmdb: TmdbSettings,
        mdbList: MdbListSettings,
        player: PlayerSettingsUiState,
    ): ProviderCredentialSnapshot = ProviderCredentialSnapshot(
        profileId = profileId,
        values = buildList {
            DebridProviders.all().forEach { provider ->
                add(
                    ProviderCredentialValue(
                        provider = ProviderCredentialIds.debrid(provider.id),
                        field = PROVIDER_API_KEY_FIELD,
                        value = debrid.apiKeyFor(provider.id).trim(),
                    ),
                )
            }
            add(ProviderCredentialValue(ProviderCredentialIds.TMDB, PROVIDER_API_KEY_FIELD, tmdb.apiKey.trim()))
            add(ProviderCredentialValue(ProviderCredentialIds.MDBLIST, PROVIDER_API_KEY_FIELD, mdbList.apiKey.trim()))
            add(
                ProviderCredentialValue(
                    ProviderCredentialIds.ANIMESKIP,
                    PROVIDER_CLIENT_ID_FIELD,
                    player.animeSkipClientId.trim(),
                ),
            )
            add(
                ProviderCredentialValue(
                    ProviderCredentialIds.INTRODB,
                    PROVIDER_API_KEY_FIELD,
                    player.introDbApiKey.trim(),
                ),
            )
        },
    )

}
