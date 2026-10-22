package com.nuvio.app.features.watched

expect object WatchedStorage {
    fun loadPayload(profileId: Int): String?
    fun savePayload(profileId: Int, payload: String)
}


/**
 * Sépare définitivement les historiques « vus » du mode invité, de chaque
 * utilisateur Better Auth et de ses profils numériques locaux.
 * Aucun historique ancien n'est repris implicitement dans le nouveau compte.
 */
internal fun accountScopedWatchedStorageKey(
    legacyKey: String,
    profileId: Int,
    cloudOwnerId: String?
): String {
    require(profileId > 0)
    return if (cloudOwnerId.isNullOrBlank()) legacyKey
        else "cloud_v1_${cloudOwnerId}_watched_$profileId"
}
