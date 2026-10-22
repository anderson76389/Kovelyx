package com.nuvio.tv.domain.model

sealed interface ProfileBackgroundSelection {
    data class Catalog(val id: String) : ProfileBackgroundSelection
    data class Custom(val url: String) : ProfileBackgroundSelection
}

fun resolveProfileBackgroundSelection(
    profile: UserProfile?,
    entitlements: CosmeticEntitlements
): ProfileBackgroundSelection? {
    if (profile == null) return null
    // Les médias personnels sont privés, importés et sauvegardés dans le
    // stockage de l'application : ils n'exigent aucun abonnement externe.
    profile.profileBackgroundUrl?.trim()
        ?.takeIf { uri ->
            uri.startsWith("file:") && uri.contains("/kovelyx/profile-media/")
        }?.let { return ProfileBackgroundSelection.Custom(it) }
    if (!entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS)) return null
    profile.profileBackgroundUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return ProfileBackgroundSelection.Custom(it)
    }
    return profile.profileBackgroundId?.let { ProfileBackgroundSelection.Catalog(it) }
}
