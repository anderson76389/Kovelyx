package com.nuvio.app.features.membership

import com.nuvio.app.features.profiles.NuvioProfile
import com.nuvio.app.features.profiles.ProfileLocalImages

sealed interface ProfileBackgroundSelection {
    data class Catalog(val id: String) : ProfileBackgroundSelection
    data class Custom(val url: String) : ProfileBackgroundSelection
}

fun resolveProfileBackground(
    profile: NuvioProfile,
    entitlements: CosmeticEntitlements,
): ProfileBackgroundSelection? {
    // Images selected from the user's computer are always free and private.
    profile.profileBackgroundUrl?.trim()?.takeIf(ProfileLocalImages::isManagedUri)?.let {
        return ProfileBackgroundSelection.Custom(it)
    }
    if (!entitlements.includes(CosmeticEntitlement.PROFILE_BACKGROUNDS)) return null
    profile.profileBackgroundUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
        return ProfileBackgroundSelection.Custom(it)
    }
    profile.profileBackgroundId?.trim()?.takeIf { it.isNotBlank() }?.let {
        return ProfileBackgroundSelection.Catalog(it)
    }
    return null
}
