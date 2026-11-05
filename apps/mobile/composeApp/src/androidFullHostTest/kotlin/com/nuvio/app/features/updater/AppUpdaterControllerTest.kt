package com.nuvio.app.features.updater

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppUpdaterControllerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: UpdatePreferences

    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kovelyx_updater", Context.MODE_PRIVATE).edit().clear().commit()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        AndroidAppUpdaterPlatform.initialize(context)
        preferences = UpdatePreferences("1.1.0")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun optimizedDebugPackageDetectsUnifiedStableReleaseWithoutDebuggableFlag() {
        // Official unified builds disable Android's debuggable flag for production,
        // but keep the .debug package ID for backwards-compatible upgrades.
        assertTrue(AndroidAppUpdaterPlatform.isDebugBuildIdentity("com.kovelyx.mobile.debug", false))
        assertTrue(AndroidAppUpdaterPlatform.isDebugBuildIdentity("com.kovelyx.mobile.debug", true))

        val release = """[
            {"tag_name":"v1.1.858","name":"Kovelyx 1.1.858","assets":[
                {"name":"Kovelyx-Android-v1.1.858.apk",
                 "browser_download_url":"https://github.com/anderson76389/Kovelyx/releases/download/v1.1.858/Kovelyx-Android-v1.1.858.apk"}
            ]}
        ]"""
        val selected = AppUpdaterRepository.selectUpdate(
            release,
            UpdateChannel.STABLE,
            listOf("arm64-v8a"),
            family = AndroidApkReleaseFamily.DEBUG,
        )
        assertEquals("v1.1.858", selected?.tag)
        assertEquals("Kovelyx-Android-v1.1.858.apk", selected?.assetName)
        assertTrue(VersionUtils.isRemoteNewer(selected?.tag, "1.1.846"))
        assertFalse(VersionUtils.isRemoteNewer(selected?.tag, "1.1.858"))
    }

    @Test
    fun permanentSignedPackageNeverSelectsDebugReleaseFamily() {
        // The permanent signing track has a DIFFERENT Android applicationId.
        // A development debuggable flag must never offer incompatible APKs.
        assertFalse(AndroidAppUpdaterPlatform.isDebugBuildIdentity("com.kovelyx.mobile", true))
        assertFalse(AndroidAppUpdaterPlatform.isDebugBuildIdentity("com.kovelyx.mobile", false))
        val release = """[
            {"tag_name":"v1.1.858","assets":[
                {"name":"Kovelyx-Android-v1.1.858.apk",
                 "browser_download_url":"https://example.com/debug.apk"}
            ]}
        ]"""
        assertNull(
            AppUpdaterRepository.selectUpdate(
                release,
                UpdateChannel.STABLE,
                listOf("arm64-v8a"),
                family = AndroidApkReleaseFamily.SIGNED,
            ),
        )
    }

    @Test
    fun everyPublishedNewerVersionAutomaticallyDisplaysTheTopBanner() {
        // The existing app may be one or many releases behind; all can upgrade.
        for (installed in listOf("1.1.821", "1.1.846", "1.1.858")) {
            val controller = AppUpdaterController(
                scope = scope,
                preferences = preferences,
                fetchUpdate = { Result.success(update("v1.1.900")) },
                installedVersion = installed,
            )

            controller.ensureAutoCheckStarted()

            assertTrue(controller.uiState.value.isUpdateAvailable, installed)
            assertTrue(controller.uiState.value.showDialog, installed)
            assertEquals("v1.1.900", controller.uiState.value.update?.tag)
        }
    }

    @Test
    fun automaticBannerWorksForNewReleaseEvenWhenPreviousReleaseWasIgnored() {
        AppUpdaterPlatform.setIgnoredTag("v1.1.858")
        val controller = AppUpdaterController(
            scope = scope,
            preferences = preferences,
            fetchUpdate = { Result.success(update("v1.1.900")) },
            installedVersion = "1.1.858",
        )

        controller.ensureAutoCheckStarted()

        assertTrue(controller.uiState.value.isUpdateAvailable)
        assertTrue(controller.uiState.value.showDialog)
    }

    @Test
    fun automaticBannerDoesNotOfferTheSameOrAnOlderRelease() {
        for (installed in listOf("1.1.900", "1.1.901")) {
            val controller = AppUpdaterController(
                scope = scope,
                preferences = preferences,
                fetchUpdate = { Result.success(update("v1.1.900")) },
                installedVersion = installed,
            )

            controller.ensureAutoCheckStarted()

            assertFalse(controller.uiState.value.isUpdateAvailable, installed)
            assertFalse(controller.uiState.value.showDialog, installed)
        }
    }

    @Test
    fun forcedChecksDoNotOfferEqualOrOlderVersions() {
        listOf(AppVersionConfig.VERSION_NAME, "0.0.0").forEach { tag ->
            val controller = controller { Result.success(update(tag)) }
            controller.checkForUpdates(force = true, showNoUpdateFeedback = false)

            assertFalse(controller.uiState.value.isUpdateAvailable)
            assertFalse(controller.uiState.value.showDialog)
            assertNull(controller.uiState.value.update)
        }
    }

    @Test
    fun manualChecksCanShowAnIgnoredNewerRelease() {
        AppUpdaterPlatform.setIgnoredTag("999.0.0")
        val controller = controller { Result.success(update("999.0.0")) }

        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)
        assertFalse(controller.uiState.value.showDialog)
        controller.checkForUpdates(force = true, showNoUpdateFeedback = false)
        assertTrue(controller.uiState.value.showDialog)
        assertTrue(controller.uiState.value.isUpdateAvailable)
    }

    @Test
    fun channelChangeDiscardsPendingUpdate() {
        val betaResult = CompletableDeferred<Result<AppUpdate>>()
        val controller = controller { channel ->
            if (channel == UpdateChannel.STABLE) Result.success(update("999.0.0"))
            else betaResult.await()
        }
        controller.checkForUpdates(force = true, showNoUpdateFeedback = false)
        assertTrue(controller.uiState.value.showDialog)

        preferences.setChannel(UpdateChannel.BETA)

        // A real channel change starts a fresh check, even in a Debug build.
        // The previous channel's offered release must disappear while it is pending.
        assertEquals(UpdateChannel.BETA, controller.uiState.value.updateChannel)
        assertNull(controller.uiState.value.update)
        assertFalse(controller.uiState.value.showDialog)
        assertTrue(controller.uiState.value.isChecking)

        betaResult.complete(Result.success(update("999.0.0-beta.1")))
        assertEquals("999.0.0-beta.1", controller.uiState.value.update?.tag)
        assertTrue(controller.uiState.value.showDialog)
        assertFalse(controller.uiState.value.isChecking)
    }

    @Test
    fun channelChangeChecksNewChannelAndIgnoresLateResults() {
        val context = RuntimeEnvironment.getApplication()
        context.applicationInfo.flags = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val oldResult = CompletableDeferred<Result<AppUpdate>>()
        val requestedChannels = mutableListOf<UpdateChannel>()
        val controller = controller { channel ->
            requestedChannels += channel
            if (channel == UpdateChannel.STABLE) {
                withContext(NonCancellable) { oldResult.await() }
            } else {
                Result.success(update("999.0.0-beta.1"))
            }
        }
        controller.checkForUpdates(force = false, showNoUpdateFeedback = false)
        preferences.setChannel(UpdateChannel.BETA)
        oldResult.complete(Result.success(update("999.0.0")))

        assertEquals(listOf(UpdateChannel.STABLE, UpdateChannel.BETA), requestedChannels)
        assertEquals(UpdateChannel.BETA, controller.uiState.value.updateChannel)
        assertEquals("999.0.0-beta.1", controller.uiState.value.update?.tag)
        assertTrue(controller.uiState.value.showDialog)
        assertFalse(controller.uiState.value.isChecking)
    }

    @Test
    fun noEligibleReleaseDoesNotOpenErrorDialog() {
        val controller = controller { Result.failure(NoChannelReleaseException()) }
        controller.checkForUpdates(force = true, showNoUpdateFeedback = false)

        assertFalse(controller.uiState.value.showDialog)
        assertFalse(controller.uiState.value.isChecking)
        assertNull(controller.uiState.value.errorMessage)
    }

    @Test
    fun channelChangeCancelsDebugDownloadAndClearsItsState() {
        val controller = controller { error("Debug channel changes should not check for updates") }
        controller.showDebugTestUpdate()
        controller.downloadUpdate()
        assertTrue(controller.uiState.value.isDownloading)

        preferences.setChannel(UpdateChannel.BETA)

        assertEquals(AppUpdaterUiState(updateChannel = UpdateChannel.BETA), controller.uiState.value)
    }

    private fun controller(fetch: suspend (UpdateChannel) -> Result<AppUpdate>) =
        AppUpdaterController(scope, preferences, fetch)

    private fun update(tag: String) = AppUpdate(
        tag = tag,
        title = tag,
        notes = "",
        releaseUrl = null,
        assetName = "app.apk",
        assetUrl = "https://example.com/app.apk",
        assetSizeBytes = null,
    )
}
