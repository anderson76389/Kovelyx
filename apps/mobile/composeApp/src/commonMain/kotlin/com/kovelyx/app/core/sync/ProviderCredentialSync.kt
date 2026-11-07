package com.kovelyx.app.core.sync

import com.kovelyx.app.features.debrid.DebridProviders
import com.kovelyx.app.features.debrid.DebridSettings
import com.kovelyx.app.features.mdblist.MdbListSettings
import com.kovelyx.app.features.player.PlayerSettingsUiState
import com.kovelyx.app.features.tmdb.TmdbSettings

/** Provider credentials remain private on-device; never send API keys to a retired backend. */
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
