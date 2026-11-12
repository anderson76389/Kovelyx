# Audit Nuvio des trois applications

Rapport statique généré sur le dépôt Kovelyx. Une occurrence ne prouve pas un appel réseau ;
un symbole de package historique ne prouve pas une dépendance de service.

Fichiers inspectés : 4657

## Inventaire des références à traiter

### Connexions et services (16 correspondances)

- apps/tv/app/src/main/java/com/kovelyx/tv/data/remote/ServerDiscovery.kt:123 : return listOf("nuvio.tv", "nuvioapp.space").any { domain ->
- apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/network/ServerDiscovery.kt:108 : // nuvio.tv.example.net is allowed, but api-two.nuvioapp.space is not.
- apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/network/ServerDiscovery.kt:109 : val formerService = listOf("nuvio.tv", "nuvioapp.space").any {
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:13 : "https://nuvio.tv",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:14 : "https://api.nuvio.tv",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:15 : "https://nuvio.tv:443/.well-known/kovelyx",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:18 : "https://api.nuvio.tv.",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:19 : "https://A.B.API.NUVIO.TV:8443/link",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:29 : "https://nuvio.tv.example.net",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:30 : "https://anothernuvio.tv",
- apps/desktop/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/network/ServerDiscovery.kt:105 : val retired = listOf("nuvio.tv", "nuvioapp.space").any { domain ->
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:13 : "https://nuvio.tv",
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:14 : "https://api.nuvio.tv",
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:17 : "https://A.B.API.NUVIO.TV:8443/link"
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:25 : "https://nuvio.tv.example.net",
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:27 : "https://anothernuvio.tv"

### Mises à jour et releases (0 correspondances)


### Liens annexes (30 correspondances)

- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:25 : parseAppDeepLink("nuvio://meta
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:33 : parseAppDeepLink("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json"),
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:49 : parseAppDeepLink("nuvio://series/tt0944947"),
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:57 : parseAppDeepLink("nuvio://imdb/series/tt0944947"),
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:65 : parseAppDeepLink("nuvio://tmdb/tv/1399"),
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:71 : assertNull(parseAppDeepLink("nuvio://auth/trakt
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:153 : assertFalse(callbacks.handleUrl("nuvio://meta
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:158 : assertTrue(callbacks.handleUrl("nuvio://external-player/infuse/session-1/success
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:46 : redirectUri = "nuvio://auth/simkl",
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:61 : parseSimklAuthCallback("nuvio://auth/trakt
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:64 : parseSimklAuthCallback("nuvio://auth/simkl
- apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:68 : parseSimklAuthCallback("nuvio://auth/simkl
- apps/mobile/composeApp/src/iosMain/kotlin/com/kovelyx/app/features/player/ExternalPlayerPlatform.ios.kt:106 : NSURL.URLWithString("$scheme://") 
- apps/mobile/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19 : private let downloadsUrl = URL(string: "nuvio://downloads")
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:25 : parseAppDeepLink("nuvio://meta
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:33 : parseAppDeepLink("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json"),
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:49 : parseAppDeepLink("nuvio://series/tt0944947"),
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:57 : parseAppDeepLink("nuvio://imdb/series/tt0944947"),
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:65 : parseAppDeepLink("nuvio://tmdb/tv/1399"),
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:71 : assertNull(parseAppDeepLink("nuvio://auth/trakt
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:153 : assertFalse(callbacks.handleUrl("nuvio://meta
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:158 : assertTrue(callbacks.handleUrl("nuvio://external-player/infuse/session-1/success
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:46 : redirectUri = "nuvio://auth/simkl",
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:61 : parseSimklAuthCallback("nuvio://auth/trakt
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:64 : parseSimklAuthCallback("nuvio://auth/simkl
- apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:68 : parseSimklAuthCallback("nuvio://auth/simkl
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/DesktopCollectionImportBridge.kt:236 : /** Per-user URL registration, no administrator privileges, never claims nuvio:// or stremio://. */
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/Main.kt:387 : value.startsWith("nuvio://", ignoreCase = true) //
- apps/desktop/composeApp/src/iosMain/kotlin/com/kovelyx/app/features/player/ExternalPlayerPlatform.ios.kt:106 : NSURL.URLWithString("$scheme://") 
- apps/desktop/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19 : private let downloadsUrl = URL(string: "nuvio://downloads")

### Archives et tests (18 correspondances)

- apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:29 : DeepLinkParser.parse("nuvio://meta
- apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:37 : DeepLinkParser.parse("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json")
- apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:53 : DeepLinkParser.parse("nuvio://series/tt0944947")
- apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:61 : DeepLinkParser.parse("nuvio://imdb/series/tt0944947")
- apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:69 : DeepLinkParser.parse("nuvio://tmdb/tv/1399")
- apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:75 : assertNull(DeepLinkParser.parse("nuvio://auth/trakt
- apps/tv/app/src/test/java/com/kovelyx/tv/core/torrent/TorrentEngineConfigTest.kt:113 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/tv/app/src/test/java/com/kovelyx/tv/core/torrent/TorrentEngineConfigTest.kt:129 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/tv/app/src/test/java/com/kovelyx/tv/core/torrent/TorrentEngineConfigTest.kt:147 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:135 : "https://api.nuvio.tv/.well-known/kovelyx",
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:136 : "https://api.nuvio.tv"
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:141 : "https://api.nuvio.tv.example.com/.well-known/kovelyx",
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:142 : "https://api.nuvio.tv"
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:149 : "https://nuvio.tv",
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:150 : "https://api.nuvio.tv",
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:153 : "https://A.B.API.NUVIO.TV:8443/link"
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:158 : "https://nuvio.tv.example.org",
- apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:160 : "https://anothernuvio.tv"

### Configuration générale (24 correspondances)

- apps/tv/app/src/main/java/com/kovelyx/tv/core/torrent/TorrentService.kt:318 : val stateDirectory = File(context.noBackupFilesDir, "nuvio-engine/state")
- apps/tv/app/src/main/java/com/kovelyx/tv/core/torrent/TorrentService.kt:319 : val cacheDirectory = File(context.cacheDir, "nuvio-engine")
- apps/tv/app/src/main/res/values-nl/strings.xml:1529 : <string name="profile_pin_overlay_forgot_hint">Pincode vergeten
- apps/mobile/composeApp/build.gradle.kts:258 : val nuvioEngineAppleFramework = rootProject.file("../nuvio-engine/platform/apple/NuvioEngine.xcframework")
- apps/mobile/composeApp/src/androidHostTest/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngineAndroidTest.kt:147 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/mobile/composeApp/src/androidHostTest/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngineAndroidTest.kt:163 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/mobile/composeApp/src/androidHostTest/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngineAndroidTest.kt:181 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/mobile/composeApp/src/androidMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.android.kt:474 : val stateDirectory = File(context.noBackupFilesDir, "nuvio-engine/state")
- apps/mobile/composeApp/src/androidMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.android.kt:475 : val cacheDirectory = File(context.cacheDir, "nuvio-engine")
- apps/desktop/composeApp/build.gradle.kts:489 : val nuvioEngineAppleFramework = rootProject.file("../nuvio-engine/platform/apple/NuvioEngine.xcframework")
- apps/desktop/composeApp/src/androidHostTest/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngineAndroidTest.kt:147 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/desktop/composeApp/src/androidHostTest/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngineAndroidTest.kt:163 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/desktop/composeApp/src/androidHostTest/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngineAndroidTest.kt:181 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/desktop/composeApp/src/androidMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.android.kt:474 : val stateDirectory = File(context.noBackupFilesDir, "nuvio-engine/state")
- apps/desktop/composeApp/src/androidMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.android.kt:475 : val cacheDirectory = File(context.cacheDir, "nuvio-engine")
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/p2p/DesktopEngineLibrary.kt:58 : return DesktopCache.installVersionedResources("nuvio-engine/$directory", mapOf(name to resource))
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.desktop.kt:75 : Thread({ runCatching { engine
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.desktop.kt:310 : val stateDirectory = DesktopStorage.rootDir.resolve("nuvio-engine/state").toFile()
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/p2p/P2pStreamingEngine.desktop.kt:311 : val cacheDirectory = DesktopStorage.cacheDir.resolve("nuvio-engine").toFile()
- apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/features/settings/DiscordRichPresenceStorage.desktop.kt:11 : private val store = DesktopStorage.store("nuvio_discord_rich_presence")
- apps/desktop/composeApp/src/desktopTest/kotlin/com/kovelyx/app/features/p2p/DesktopEngineTest.kt:79 : val root = Files.createTempDirectory("nuvio-engine-desktop-").toFile()
- apps/desktop/composeApp/src/desktopTest/kotlin/com/kovelyx/app/features/p2p/DesktopEngineTest.kt:99 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/desktop/composeApp/src/desktopTest/kotlin/com/kovelyx/app/features/p2p/DesktopEngineTest.kt:115 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()
- apps/desktop/composeApp/src/desktopTest/kotlin/com/kovelyx/app/features/p2p/DesktopEngineTest.kt:133 : val cacheDirectory = Files.createTempDirectory("nuvio-engine-cache-").toFile()

## Binaires hérités

- apps/desktop/composeApp/src/desktopMain/native/linux/engine/libnuvio_engine.so
- apps/desktop/composeApp/src/desktopMain/native/macos/engine/libnuvio_engine.dylib
- apps/desktop/composeApp/src/desktopMain/native/windows/engine/nuvio_engine.dll

## Références de packages Kotlin historiques

- apps/tv : 10 lignes.
- apps/mobile : 1774 lignes.
- apps/desktop : 1921 lignes.

## Variables héritées (ne pas renommer sans migrer les consommateurs)

- NUVIO_SYNC : 136
- NUVIO_JS : 12
- NUVIO_IOS_DISTRIBUTION : 12
- NUVIO_LINUX_SHORTCUT_RELATIVE_PATH : 5
- NUVIO_ENGINE_EVENT_DISK_CACHE_RECLAIMED : 4
- NUVIO_ENGINE_EVENT_STREAM_PREPARED : 4
- NUVIO_ENGINE_EVENT_STREAM_STOPPED : 4
- NUVIO_ENGINE_EVENT_TORRENT_ERROR : 4
- NUVIO_ENGINE_EVENT_TORRENT_METADATA_READY : 4
- NUVIO_ENGINE_STATUS_NO_EVENT : 4
- NUVIO_ENGINE_STATUS_OK : 4
- NUVIO_ENGINE_TORRENT_PROFILE_BALANCED : 4
- NUVIO_ENGINE_TORRENT_PROFILE_FAST : 4
- NUVIO_ENGINE_TORRENT_PROFILE_SOFT : 4
- NUVIO_ENGINE_UPLOAD_DISABLED : 4
- NUVIO_ENGINE_UPLOAD_UNLIMITED : 4
- NUVIO_GRADLE_JVMARGS : 4
- NUVIO_KOTLIN_NATIVE_JVMARGS : 4
- NUVIO_ENGINE_ROOT : 4
- NUVIO_LINUX_RPM_RUNTIME_DEPENDENCIES : 4
- NUVIO_LINUX_SHORTCUT_STARTUP_WM_CLASS : 4
- NUVIO_LINUX_DEB_RUNTIME_DEPENDENCIES : 3
- NUVIO_LINUX_SHORTCUT_MIME_TYPES : 3
- NUVIO_SHARED_POOL_MAX_IDLE : 2
- NUVIO_LANE_POOL_MAX_IDLE : 2
- NUVIO_RELEASE_STORE_FILE : 2
- NUVIO_RELEASE_STORE_PASSWORD : 2
- NUVIO_RELEASE_KEY_ALIAS : 2
- NUVIO_RELEASE_KEY_PASSWORD : 2
- NUVIO_ANDROID_DISTRIBUTION : 2
- NUVIO_LOG_PACKAGE : 2
- NUVIO_MACOS_NOTARY_PASSWORD : 2
- NUVIO_MACOS_NOTARY_KEYCHAIN_PROFILE : 2
- NUVIO_MACOS_SIGNING_IDENTITY : 2
- NUVIO_DESKTOP_VERSION_NAME : 2
- NUVIO_DESKTOP_VERSION_CODE : 2
- NUVIO_DESKTOP_SMOKE_PLAYER_URL : 2
- NUVIO_LINUX_SHORTCUT_NAME : 2
- NUVIO_LINUX_SHORTCUT_COMMENT : 2
- NUVIO_LINUX_SHORTCUT_CATEGORIES : 2

## Limites et vérifications indispensables

- Tester les trois applications en surveillant les appels réseau réels et sans accès aux domaines Nuvio.
- Vérifier les parcours de comptes Better Auth et de synchronisation Cloudflare D1 sur les trois appareils.
- Conserver les sources et licences des bibliothèques MPV, libass et moteurs livrés sous forme binaire.
- Migrer les URL de support et connexion sans inventer de faux services.
- Tester la signature, les identifiants d'application, les mises à jour et la conservation des données.
- Ne pas supprimer les mentions d'attribution/licence Nuvio obligatoires.

Absence de résultat dans cet audit ne constituerait pas une garantie 100 % indépendante.
