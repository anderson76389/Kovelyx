package com.kovelyx.app

import android.content.Intent
import android.content.res.Configuration
import com.kovelyx.app.features.autosync.AutoSyncPreferencesAndroid
import android.os.Bundle
import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kovelyx.app.core.build.KovelyxIntegrationCredentials
import com.kovelyx.app.core.auth.AuthStorage
import com.kovelyx.app.core.network.ServerConfigurationStorage
import com.kovelyx.app.core.diagnostics.SentryInitializer
import com.kovelyx.app.core.deeplink.handleAppUrl
import com.kovelyx.app.core.storage.PlatformLocalAccountDataCleaner
import com.kovelyx.app.core.sync.SyncClientIdentityStorage
import com.kovelyx.app.features.addons.AddonHttpClientProvider
import com.kovelyx.app.features.addons.AddonStorage
import com.kovelyx.app.features.collection.CollectionMobileSettingsStorage
import com.kovelyx.app.features.collection.CollectionStorage
import com.kovelyx.app.features.debrid.DebridSettingsStorage
import com.kovelyx.app.features.downloads.DownloadsLiveStatusPlatform
import com.kovelyx.app.features.downloads.DownloadsPlatformDownloader
import com.kovelyx.app.features.downloads.DownloadsStorage
import com.kovelyx.app.features.library.LibraryDisplaySettingsStorage
import com.kovelyx.app.features.membership.MemberAssetStorage
import com.kovelyx.app.features.library.LibraryStorage
import com.kovelyx.app.features.details.MetaScreenSettingsStorage
import com.kovelyx.app.features.home.HomeCatalogSettingsStorage
import com.kovelyx.app.features.mdblist.MdbListSettingsStorage
import com.kovelyx.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.kovelyx.app.features.notifications.EpisodeReleaseNotificationsStorage
import com.kovelyx.app.features.player.PlayerSettingsStorage
import com.kovelyx.app.features.player.PlayerTrackPreferenceStorage
import com.kovelyx.app.features.player.ExternalPlayerPlatform
import com.kovelyx.app.features.player.SubtitleFileCache
import com.kovelyx.app.features.player.PlayerPictureInPictureManager
import com.kovelyx.app.features.player.PipRemoteActionReceiver
import com.kovelyx.app.features.p2p.P2pSettingsStorage
import com.kovelyx.app.features.p2p.P2pStreamingEngine
import com.kovelyx.app.features.plugins.PluginStorage
import com.kovelyx.app.features.profiles.AvatarStorage
import com.kovelyx.app.features.profiles.ProfilePinCacheStorage
import com.kovelyx.app.features.profiles.ProfileStorage
import com.kovelyx.app.features.details.SeasonViewModeStorage
import com.kovelyx.app.features.search.DiscoverSelectionStorage
import com.kovelyx.app.features.search.SearchHistoryStorage
import com.kovelyx.app.features.settings.SentrySettingsStorage
import com.kovelyx.app.features.settings.AppIconPlatform
import com.kovelyx.app.features.settings.ThemeSettingsStorage
import com.kovelyx.app.features.trakt.TraktAuthStorage
import com.kovelyx.app.features.trakt.TraktCommentsStorage
import com.kovelyx.app.features.trakt.TraktLibraryStorage
import com.kovelyx.app.features.trakt.TraktSettingsStorage
import com.kovelyx.app.features.mdblist.PlatformMdbListAuthPersistence
import com.kovelyx.app.features.mdblist.PlatformMdbListSyncStorage
import com.kovelyx.app.features.servers.ServerStorage
import com.kovelyx.app.features.simkl.SimklAuthStorage
import com.kovelyx.app.features.simkl.SimklSyncStorage
import com.kovelyx.app.features.tmdb.TmdbSettingsStorage
import com.kovelyx.app.features.updater.AndroidAppUpdaterPlatform
import com.kovelyx.app.core.ui.CardDepthStyleStorage
import com.kovelyx.app.core.ui.PosterCardStyleStorage
import com.kovelyx.app.core.poster.CustomPosterUrlStorage
import com.kovelyx.app.features.watched.WatchedStorage
import com.kovelyx.app.features.streams.StreamLinkCacheStorage
import com.kovelyx.app.features.streams.StreamBadgeSettingsStorage
import com.kovelyx.app.features.streams.BingeGroupCacheStorage
import com.kovelyx.app.features.watchprogress.ContinueWatchingEnrichmentStorage
import com.kovelyx.app.features.watchprogress.ContinueWatchingPreferencesStorage
import com.kovelyx.app.features.watchprogress.ResumePromptStorage
import com.kovelyx.app.features.watchprogress.WatchProgressStorage

open class MainActivity : AppCompatActivity() {
    private var pipRemoteActionReceiver: PipRemoteActionReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(
                scrim = 0xFF020404.toInt(),
            ),
        )
        ThemeSettingsStorage.initialize(applicationContext)
        // Identifiants de l'application OAuth choisis par l'utilisateur : stockage privé local.
        // Aucune clé d'un autre projet n'est incluse dans l'APK.
        val oauthPreferences = applicationContext.getSharedPreferences(
            "kovelyx_oauth_credentials", MODE_PRIVATE,
        )
        KovelyxIntegrationCredentials.initializeMobile(
            clientId = oauthPreferences.getString("trakt_client_id", "").orEmpty(),
            clientSecret = oauthPreferences.getString("trakt_client_secret", "").orEmpty(),
            mdbListId = oauthPreferences.getString("mdblist_client_id", "").orEmpty(),
            onSave = { traktId, traktSecret, mdblistId ->
                check(
                    oauthPreferences.edit()
                        .putString("trakt_client_id", traktId)
                        .putString("trakt_client_secret", traktSecret)
                        .putString("mdblist_client_id", mdblistId)
                        .commit()
                ) { "Impossible de sauvegarder les identifiants OAuth sur cet appareil." }
            },
        )
        AppIconPlatform.initialize(applicationContext)
        SentrySettingsStorage.initialize(applicationContext)
        SentryInitializer.start(application)
        super.onCreate(savedInstanceState)
        AutoSyncPreferencesAndroid.initialize(applicationContext) // AutoSync hook
        window.setBackgroundDrawableResource(R.color.nuvio_background)
        pipRemoteActionReceiver = PipRemoteActionReceiver.register(this)
        SyncClientIdentityStorage.initialize(applicationContext)
        AddonHttpClientProvider.initialize(applicationContext)
        AddonStorage.initialize(applicationContext)
        AuthStorage.initialize(applicationContext)
        ServerConfigurationStorage.initialize(applicationContext)
        LibraryStorage.initialize(applicationContext)
        WatchedStorage.initialize(applicationContext)
        MetaScreenSettingsStorage.initialize(applicationContext)
        com.kovelyx.app.features.shuffle.EpisodeShuffleStorage.initialize(applicationContext)
        HomeCatalogSettingsStorage.initialize(applicationContext)
        PlayerSettingsStorage.initialize(applicationContext)
        PlayerTrackPreferenceStorage.initialize(applicationContext)
        P2pSettingsStorage.initialize(applicationContext)
        P2pStreamingEngine.initialize(applicationContext)
        ExternalPlayerPlatform.initialize(applicationContext)
        SubtitleFileCache.initialize(applicationContext)
        ProfileStorage.initialize(applicationContext)
        AvatarStorage.initialize(applicationContext)
        ProfilePinCacheStorage.initialize(applicationContext)
        MemberAssetStorage.initialize(applicationContext)
        DiscoverSelectionStorage.initialize(applicationContext)
        SearchHistoryStorage.initialize(applicationContext)
        SeasonViewModeStorage.initialize(applicationContext)
        PosterCardStyleStorage.initialize(applicationContext)
        CustomPosterUrlStorage.initialize(applicationContext)
        CardDepthStyleStorage.initialize(applicationContext)
        DebridSettingsStorage.initialize(applicationContext)
        TmdbSettingsStorage.initialize(applicationContext)
        MdbListSettingsStorage.initialize(applicationContext)
        TraktAuthStorage.initialize(applicationContext)
        TraktCommentsStorage.initialize(applicationContext)
        TraktLibraryStorage.initialize(applicationContext)
        TraktSettingsStorage.initialize(applicationContext)
        PlatformMdbListAuthPersistence.initialize(applicationContext)
        ServerStorage.initialize(applicationContext)
        PlatformMdbListSyncStorage.initialize(applicationContext)
        SimklAuthStorage.initialize(applicationContext)
        SimklSyncStorage.initialize(applicationContext)
        LibraryDisplaySettingsStorage.initialize(applicationContext)
        ContinueWatchingPreferencesStorage.initialize(applicationContext)
        ResumePromptStorage.initialize(applicationContext)
        ContinueWatchingEnrichmentStorage.initialize(applicationContext)
        EpisodeReleaseNotificationsStorage.initialize(applicationContext)
        WatchProgressStorage.initialize(applicationContext)
        StreamLinkCacheStorage.initialize(applicationContext)
        StreamBadgeSettingsStorage.initialize(applicationContext)
        BingeGroupCacheStorage.initialize(applicationContext)
        PluginStorage.initialize(applicationContext)
        CollectionMobileSettingsStorage.initialize(applicationContext)
        CollectionStorage.initialize(applicationContext)
        DownloadsStorage.initialize(applicationContext)
        DownloadsPlatformDownloader.initialize(applicationContext)
        DownloadsLiveStatusPlatform.initialize(applicationContext)
        AndroidAppUpdaterPlatform.initialize(applicationContext)
        PlatformLocalAccountDataCleaner.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.bindActivity(this)
        handleIncomingAppIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAppIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerPictureInPictureManager.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPictureInPictureManager.onPictureInPictureModeChanged(this, isInPictureInPictureMode)
    }

    override fun onDestroy() {
        EpisodeReleaseNotificationPlatform.unbindActivity(this)
        val receiver = pipRemoteActionReceiver
        if (receiver != null) {
            runCatching { unregisterReceiver(receiver) }
            pipRemoteActionReceiver = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (EpisodeReleaseNotificationPlatform.handlePermissionRequestResult(requestCode, grantResults)) {
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun handleIncomingAppIntent(intent: Intent?) {
        val appUrl = intent?.dataString?.trim().orEmpty()
        if (appUrl.isBlank()) return
        handleAppUrl(appUrl)
    }
}
