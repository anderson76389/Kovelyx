package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppUpdaterRepositoryTest {
    @Test
    fun channelsUseTheSameReleaseEndpointsAsTv() {
        assertEquals("releases?per_page=100", AppUpdaterRepository.releasePath(UpdateChannel.STABLE))
        assertEquals("releases?per_page=100", AppUpdaterRepository.releasePath(UpdateChannel.BETA))
    }

    @Test
    fun stableAcceptsReleasesFromAnyBranch() {
        listOf("main", "master", "cmp-rewrite", "1234567").forEach { branch ->
            val response = """[{
                "tag_name": "v1.1.0", "target_commitish": "$branch",
                "assets": [{"name": "Kovelyx-Android-app.apk", "browser_download_url": "https://example.com/app.apk"}]
            }]"""
            assertEquals("v1.1.0", select(response, UpdateChannel.STABLE)?.tag)
        }
    }

    @Test
    fun stableRejectsLegacyBetaNamesEvenIfGithubMarksThemStable() {
        val response = """[{
            "tag_name": "1.1.0", "name": "Beta 1.1.0 Hotfix",
            "assets": [{"name": "Kovelyx-Android-app.apk", "browser_download_url": "https://example.com/app.apk"}]
        }]"""
        assertNull(select(response, UpdateChannel.STABLE))
    }

    @Test
    fun betaSkipsReleasesWithoutAnApkAndSortsByVersion() {
        val response = """[
            {"tag_name": "1.1.0-beta.9", "assets": [
                {"name": "Kovelyx-Android-app.apk", "browser_download_url": "https://example.com/beta9.apk"}]},
            {"tag_name": "1.2.0", "assets": [
                {"name": "Kovelyx-Android-app.ipa", "browser_download_url": "https://example.com/app.ipa"}]},
            {"tag_name": "1.1.0-beta.10", "assets": [
                {"name": "Kovelyx-Android-app.apk", "browser_download_url": "https://example.com/beta10.apk"}]}
        ]"""
        assertEquals("1.1.0-beta.10", select(response, UpdateChannel.BETA)?.tag)
    }

    @Test
    fun betaReceivesFinalPromotion() {
        val response = """[
            {"tag_name": "1.1.0-beta.10", "prerelease": true, "assets": [
                {"name": "Kovelyx-Android-app.apk", "browser_download_url": "https://example.com/beta.apk"}]},
            {"tag_name": "1.1.0", "assets": [
                {"name": "Kovelyx-Android-app.apk", "browser_download_url": "https://example.com/stable.apk"}]}
        ]"""
        assertEquals("1.1.0", select(response, UpdateChannel.BETA)?.tag)
    }

    @Test
    fun apkSelectionPreservesDeviceAbiPreference() {
        val response = """[{
            "tag_name": "1.1.0", "assets": [
                {"name": "Kovelyx-Android-app-universal.apk", "browser_download_url": "https://example.com/universal.apk"},
                {"name": "Kovelyx-Android-app-arm64-v8a.apk", "browser_download_url": "https://example.com/arm64.apk"},
                {"name": "Kovelyx-Android-app-x86_64.apk", "browser_download_url": "https://example.com/x86.apk"}
            ]
        }]"""
        assertEquals("Kovelyx-Android-app-arm64-v8a.apk", select(response, UpdateChannel.STABLE)?.assetName)
        assertEquals(
            "Kovelyx-Android-app-universal.apk",
            AppUpdaterRepository.selectUpdate(response, UpdateChannel.STABLE, listOf("armeabi-v7a"))?.assetName,
        )
    }


    @Test
    fun signedAndroidReleaseUsesIndependentSemanticTag() {
        val response = """[
            {"tag_name":"v0.1.377-alpha","prerelease":true,"assets":[
                {"name":"Kovelyx-Windows-Setup-v0.1.377-alpha.exe","browser_download_url":"https://example.com/windows.exe"}]},
            {"tag_name":"android-v0.5.11-beta.2","prerelease":true,"assets":[
                {"name":"Kovelyx-Android-android-v0.5.11-beta.2-universal.apk","browser_download_url":"https://example.com/mobile.apk"}]},
            {"tag_name":"tv-v2.0.0-beta.3","prerelease":true,"assets":[
                {"name":"Kovelyx-TV-tv-v2.0.0-beta.3-universal.apk","browser_download_url":"https://example.com/tv.apk"}]},
            {"tag_name":"android-preview-aabbbccc1111","prerelease":true,"assets":[
                {"name":"Kovelyx-Android-android-preview-aabbbccc1111.apk","browser_download_url":"https://example.com/debug.apk"}]}
        ]"""
        val update = select(response, UpdateChannel.BETA)
        assertEquals("android-v0.5.11-beta.2", update?.tag)
        assertEquals("Kovelyx-Android-android-v0.5.11-beta.2-universal.apk", update?.assetName)
        assertEquals(null, select(response, UpdateChannel.STABLE))
    }

    @Test
    fun stableSearchIgnoresNewerWindowsAndTvReleases() {
        val response = """[
            {"tag_name":"tv-v9.0.0","assets":[{"name":"Kovelyx-TV-tv-v9.0.0-universal.apk","browser_download_url":"https://example.com/tv.apk"}]},
            {"tag_name":"v0.1.377","assets":[{"name":"Kovelyx-Windows-Setup-v0.1.377.exe","browser_download_url":"https://example.com/win.exe"}]},
            {"tag_name":"android-v1.0.0","assets":[{"name":"Kovelyx-Android-android-v1.0.0-universal.apk","browser_download_url":"https://example.com/mobile.apk"}]}
        ]"""
        assertEquals("android-v1.0.0", select(response, UpdateChannel.STABLE)?.tag)
    }

    private fun select(response: String, channel: UpdateChannel): AppUpdate? =
        AppUpdaterRepository.selectUpdate(response, channel, listOf("arm64-v8a"))
}
