package com.nuvio.app.features.updater

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class VersionUtilsTest {
    @Test
    fun `stable release is newer than its prerelease`() {
        assertTrue(VersionUtils.isRemoteNewer("1.1.0", "1.1.0-rc.2"))
    }

    @Test
    fun `beta identifiers use numeric ordering`() {
        assertTrue(VersionUtils.isRemoteNewer("1.1.0-beta.10", "1.1.0-beta.9"))
    }

    @Test
    fun `prerelease for a later minor is newer than current stable`() {
        assertTrue(VersionUtils.isRemoteNewer("1.1.0-beta.1", "1.0.3"))
    }

    @Test
    fun `stable channel patch is not newer than later minor beta`() {
        assertFalse(VersionUtils.isRemoteNewer("1.0.4", "1.1.0-beta.2"))
    }

    @Test
    fun `version prefix and build metadata do not affect precedence`() {
        assertFalse(VersionUtils.isRemoteNewer("v1.0.0+18", "1.0.0+17"))
    }

    @Test
    fun `invalid remote version is not offered`() {
        assertFalse(VersionUtils.isRemoteNewer("latest", "1.0.0"))
    }

    @Test
    fun signedAndroidReleaseTagsCompareWithInstalledVersion() {
        assertTrue(VersionUtils.isRemoteNewer("android-v0.6.0-beta.1", "0.5.11"))
        assertFalse(VersionUtils.isRemoteNewer("android-v0.5.10", "0.5.11"))
        assertFalse(VersionUtils.isRemoteNewer("tv-v9.0.0", "0.5.11"))
        assertFalse(VersionUtils.isRemoteNewer("android-preview-aabbccddee11", "0.5.11"))
    }

    @Test
    fun `current beta naming is recognized as prerelease`() {
        assertTrue(VersionUtils.isPrerelease("0.8.12-beta"))
    }
    @Test
    fun `shared release version is newer than previously installed Android app`() {
        assertFalse(VersionUtils.isRemoteNewer("v0.1.549-alpha", "0.5.10"))
        assertTrue(VersionUtils.isRemoteNewer("v1.1.550-alpha", "0.5.10"))
        assertTrue(VersionUtils.isRemoteNewer("v1.1.551-alpha", "1.1.550-alpha"))
        assertFalse(VersionUtils.isRemoteNewer("v1.1.550-alpha", "1.1.550-alpha"))
    }

}
