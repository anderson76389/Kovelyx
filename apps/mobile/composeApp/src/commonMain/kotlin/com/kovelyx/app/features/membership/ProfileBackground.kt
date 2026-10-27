package com.kovelyx.app.features.membership

import com.kovelyx.app.features.profiles.NuvioProfile

sealed interface ProfileBackgroundSelection {
    data class Catalog(val id: String) : ProfileBackgroundSelection
    data class Custom(val url: String) : ProfileBackgroundSelection
}

fun resolveProfileBackground(
    profile: NuvioProfile,
    entitlements: CosmeticEntitlements,
): ProfileBackgroundSelection? {
    // Un fond personnel déjà enregistré reste visible sans droit d'accès
    // au catalogue de fonds historiques (notamment en mode Cloudflare).
    profile.profileBackgroundUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
        return ProfileBackgroundSelection.Custom(it)
    }
    if (!entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS)) return null
    profile.profileBackgroundId?.trim()?.takeIf { it.isNotBlank() }?.let {
        return ProfileBackgroundSelection.Catalog(it)
    }
    return null
}
