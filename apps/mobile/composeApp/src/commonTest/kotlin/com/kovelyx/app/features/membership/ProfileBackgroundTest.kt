package com.kovelyx.app.features.membership

import com.kovelyx.app.features.profiles.NuvioProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileBackgroundTest {
    @Test
    fun backgroundDoesNotRenderWithoutEntitlement() {
        val profile = NuvioProfile(profileBackgroundId = "aurora")

        assertNull(resolveProfileBackground(profile, CosmeticEntitlements.None))
    }

    @Test
    fun customBackgroundWorksWithoutLegacyCatalogEntitlement() {
        val profile = NuvioProfile(profileBackgroundUrl = "file:///data/user/0/com.kovelyx.mobile/files/fond.png")
        assertEquals(
            ProfileBackgroundSelection.Custom("file:///data/user/0/com.kovelyx.mobile/files/fond.png"),
            resolveProfileBackground(profile, CosmeticEntitlements.None),
        )
    }

    @Test
    fun customBackgroundTakesPriorityWhenEntitled() {
        val profile = NuvioProfile(
            profileBackgroundId = "aurora",
            profileBackgroundUrl = "https://example.com/background.png",
        )
        val entitlements = CosmeticEntitlements(setOf(CosmeticEntitlement.PROFILE_BACKGROUNDS))

        assertEquals(
            ProfileBackgroundSelection.Custom("https://example.com/background.png"),
            resolveProfileBackground(profile, entitlements),
        )
    }
}
