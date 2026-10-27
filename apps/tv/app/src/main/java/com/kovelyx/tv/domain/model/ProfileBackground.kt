package com.kovelyx.tv.domain.model

sealed interface ProfileBackgroundSelection {
    data class Catalog(val id: String) : ProfileBackgroundSelection
    data class Custom(val url: String) : ProfileBackgroundSelection
}

fun resolveProfileBackgroundSelection(
    profile: UserProfile?,
    entitlements: CosmeticEntitlements
): ProfileBackgroundSelection? {
    if (profile == null) return null
    // Une image personnelle appartient au profil : son affichage ne doit
    // pas dépendre d'un droit d'accès à l'ancien catalogue de fonds.
    profile.profileBackgroundUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
        return ProfileBackgroundSelection.Custom(it)
    }
    // Seuls les fonds du catalogue restent soumis à leurs droits d'accès.
    if (!entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS)) return null
    return profile.profileBackgroundId?.let { ProfileBackgroundSelection.Catalog(it) }
}
