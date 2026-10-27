package com.nuvio.app.features.membership

import com.nuvio.app.features.profiles.NuvioProfile
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
    fun importedBackgroundWorksWithoutMembership() {
        val uri = "file:///data/user/0/com.kovelyx/files/kovelyx-profile-media/fond-local.png"
        val profile = NuvioProfile(profileBackgroundId = "aurora", profileBackgroundUrl = uri)

        assertEquals(
            ProfileBackgroundSelection.Custom(uri),
            resolveProfileBackground(profile, CosmeticEntitlements.None),
        )
    }

    @Test
    fun webCustomBackgroundWorksWithoutMembership() {
        val profile = NuvioProfile(profileBackgroundUrl = "https://example.com/mon-fond.gif")

        assertEquals(
            ProfileBackgroundSelection.Custom("https://example.com/mon-fond.gif"),
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
