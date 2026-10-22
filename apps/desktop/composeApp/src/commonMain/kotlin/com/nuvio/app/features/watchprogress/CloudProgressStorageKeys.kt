package com.nuvio.app.features.watchprogress

/** Les profils invités historiques restent lisibles. Aucun ancien progrès
 * d'un compte invité/tiers n'est adopté silencieusement par un compte D1.
 */
internal fun accountScopedProgressStorageKey(
    legacyKey: String,
    profileId: Int,
    cloudOwnerId: String?
): String {
    require(profileId > 0)
    return if (cloudOwnerId.isNullOrBlank()) legacyKey
        else "cloud_v1_${cloudOwnerId}_watch_progress_$profileId"
}
