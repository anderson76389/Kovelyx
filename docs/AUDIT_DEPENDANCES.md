# Audit statique des références réseau

Rapport **indicatif** ; il ne certifie ni la compilation ni l'indépendance. Certaines URLs peuvent appartenir aux documents, aux tests ou être construites dynamiquement.

## TV

Fichiers texte examinés : **1364** ; références statiques à Nuvio/NuvioMedia : **6803**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 227 |
| `www.themoviedb.org` | 131 |
| `trakt.tv` | 39 |
| `media.example.com` | 37 |
| `h` | 34 |
| `cdn.example` | 32 |
| `www.imdb.com` | 31 |
| `cdn.example.com` | 28 |
| `...` | 26 |
| `backend.example.com` | 26 |
| `www.youtube.com` | 24 |
| `addon.example` | 22 |
| `.` | 20 |
| `www.apache.org` | 19 |
| `127.0.0.1` | 16 |
| `cdn` | 16 |
| `x` | 16 |
| `video.example.com` | 16 |
| `schemas.android.com` | 15 |
| `media.trakt.tv` | 14 |
| `image` | 14 |
| `github.com` | 13 |
| `simkl.com` | 13 |
| `api.aioratings.com` | 13 |
| `example.test` | 12 |
| `image.tmdb.org` | 10 |
| `localhost` | 10 |
| `mdblist.com` | 9 |
| `192.168.1.10` | 9 |
| `postersplus.example.com` | 9 |

### Références à vérifier en priorité

- `apps/tv/app/build.gradle.kts:109`
- `apps/tv/app/build.gradle.kts:418`
- `apps/tv/app/build.gradle.kts:450`
- `apps/tv/baselineprofile/build.gradle.kts:8`
- `apps/tv/scripts/perf_baseline.sh:22`
- `apps/tv/scripts/perf_baseline.sh:27`
- `apps/tv/app/src/testFull/java/com/nuvio/tv/core/plugin/FetchResponseHeadersTest.kt:1`
- `apps/tv/app/src/testFull/java/com/nuvio/tv/core/plugin/cloudstream/HomeViewModelCompatStubTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/core/profile/ProtectedProfilePreferencesTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/player/Media3VideoDecodeSmokeTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/player/PlayerProgressFillTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:15`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:16`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:17`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:18`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:19`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/library/LibraryListDialogsTest.kt:20`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:20`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:21`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:22`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:23`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:24`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/TrackingSettingsOverviewTest.kt:25`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsOrganizationTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsOrganizationTest.kt:18`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsOrganizationTest.kt:19`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsOrganizationTest.kt:20`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsOrganizationTest.kt:21`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/PlaybackSettingsOrganizationTest.kt:22`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/MdbListAccountDialogTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/MdbListAccountDialogTest.kt:17`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/MdbListAccountDialogTest.kt:18`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/settings/MdbListAccountDialogTest.kt:19`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:32`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:33`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:34`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:35`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:36`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:37`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:38`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:39`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:40`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/ui/screens/detail/EpisodeShuffleTvTest.kt:41`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveLibraryTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveLibraryTest.kt:5`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveLibraryTest.kt:8`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveLibraryTest.kt:9`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveLibraryTest.kt:10`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveLibraryTest.kt:11`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveScrobbleTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveScrobbleTest.kt:5`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveScrobbleTest.kt:6`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListTimestampTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveAccountTest.kt:1`
- `apps/tv/app/src/androidTest/java/com/nuvio/tv/data/mdblist/MdbListLiveAccountTest.kt:5`
- `apps/tv/app/src/full/java/com/lagradost/cloudstream3/ui/home/HomeViewModel.kt:11`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:3`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:4`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:5`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:6`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:7`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:8`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:9`
- `apps/tv/app/src/full/java/com/nuvio/tv/di/PluginModule.kt:10`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/AbiSelector.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/AbiSelector.kt:4`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkInstaller.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkInstaller.kt:11`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:6`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:7`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:8`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkDownloader.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:3`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:4`
- …et 6723 autres correspondances (plafond d'affichage).

## Android

Fichiers texte examinés : **1406** ; références statiques à Nuvio/NuvioMedia : **62**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 184 |
| `github.com` | 121 |
| `www.themoviedb.org` | 110 |
| `example.test` | 68 |
| `schemas.android.com` | 47 |
| `media.example.com` | 37 |
| `www.imdb.com` | 25 |
| `.` | 21 |
| `original.com` | 20 |
| `api.trakt.tv` | 16 |
| `mdblist.com` | 15 |
| `api.aioratings.com` | 13 |
| `backend.example.com` | 13 |
| `api.ratingposterdb.com` | 12 |
| `cdn.example.com` | 12 |
| `simkl.com` | 12 |
| `media.trakt.tv` | 12 |
| `postersplus.example.com` | 9 |
| `img` | 9 |
| `pm` | 9 |
| `api.mdblist.com` | 8 |
| `catalog.example` | 8 |
| `trakt.tv` | 7 |
| `www.youtube.com` | 6 |
| `a.com` | 5 |
| `192.168.1.10` | 5 |
| `btttr.cc` | 4 |
| `addon.com` | 4 |
| `v3-cinemeta.strem.io` | 4 |
| `image.tmdb.org` | 4 |

### Références à vérifier en priorité

- `apps/mobile/MPVKit/Package.swift:304`
- `apps/mobile/scripts/prepare-ios-dependencies.sh:25`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:25`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:33`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:49`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:57`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:65`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:71`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:13`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:14`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:15`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:18`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:19`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:29`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/network/ServerDiscoveryPolicyTest.kt:30`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:153`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:158`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:46`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:61`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:64`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:68`
- `apps/mobile/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/ExternalPlayerPlatform.ios.kt:106`
- `apps/mobile/composeApp/src/commonMain/kotlin/com/nuvio/app/core/network/ServerDiscovery.kt:109`
- `apps/mobile/composeApp/src/commonMain/kotlin/com/nuvio/app/core/network/ServerDiscovery.kt:110`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1951`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1983`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1984`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-vi/strings.xml:357`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-vi/strings.xml:400`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-vi/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bg/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ur/strings.xml:357`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ur/strings.xml:400`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ur/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-el/strings.xml:142`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-el/strings.xml:183`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-el/strings.xml:184`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2042`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2074`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2075`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:358`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:402`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-hr/strings.xml:357`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-hr/strings.xml:400`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-hr/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2185`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2217`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2218`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-es/strings.xml:355`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-es/strings.xml:398`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-es/strings.xml:399`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:395`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:396`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nl/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nl/strings.xml:395`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nl/strings.xml:396`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nb/strings.xml:511`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1273`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1274`
- `apps/mobile/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`

## Windows

Fichiers texte examinés : **1781** ; références statiques à Nuvio/NuvioMedia : **57**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 183 |
| `github.com` | 136 |
| `www.themoviedb.org` | 110 |
| `example.test` | 76 |
| `schemas.android.com` | 49 |
| `media.example.com` | 37 |
| `www.imdb.com` | 25 |
| `.` | 21 |
| `original.com` | 20 |
| `simkl.com` | 16 |
| `api.trakt.tv` | 16 |
| `mdblist.com` | 15 |
| `api.ratingposterdb.com` | 14 |
| `api.aioratings.com` | 13 |
| `backend.example.com` | 13 |
| `cdn.example.com` | 12 |
| `media.trakt.tv` | 12 |
| `example.invalid` | 11 |
| `postersplus.example.com` | 9 |
| `img` | 9 |
| `image.tmdb.org` | 9 |
| `pm` | 9 |
| `trakt.tv` | 9 |
| `example.org` | 8 |
| `api.mdblist.com` | 8 |
| `catalog.example` | 8 |
| `anderson76389.github.io` | 7 |
| `media.w3.org` | 7 |
| `btttr.cc` | 6 |
| `www.youtube.com` | 6 |

### Références à vérifier en priorité

- `apps/desktop/MPVKit/Package.swift:304`
- `apps/desktop/scripts/prepare-ios-dependencies.sh:25`
- `apps/desktop/composeApp/src/desktopMain/kotlin/com/nuvio/app/DesktopCollectionImportBridge.kt:229`
- `apps/desktop/composeApp/src/desktopMain/kotlin/com/nuvio/app/Main.kt:350`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:25`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:33`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:49`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:57`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:65`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:71`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:153`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:158`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:46`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:61`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:64`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:68`
- `apps/desktop/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/ExternalPlayerPlatform.ios.kt:106`
- `apps/desktop/composeApp/src/commonMain/kotlin/com/nuvio/app/core/network/ServerDiscovery.kt:50`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1952`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1984`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1985`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-vi/strings.xml:357`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-vi/strings.xml:400`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-vi/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bg/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ur/strings.xml:357`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ur/strings.xml:400`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ur/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-el/strings.xml:142`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-el/strings.xml:183`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-el/strings.xml:184`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2045`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2077`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2078`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bn/strings.xml:358`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bn/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bn/strings.xml:402`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-hr/strings.xml:357`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-hr/strings.xml:400`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-hr/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2186`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2218`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2219`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-es/strings.xml:355`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-es/strings.xml:398`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-es/strings.xml:399`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:395`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:396`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nl/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nl/strings.xml:395`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nl/strings.xml:396`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nb/strings.xml:511`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1273`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1274`
- `apps/desktop/scripts/linux/test-linux-deb-tools.sh:30`
- `apps/desktop/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`

## Dépendances natives manquantes

- **TV / .kovelyx-gitlinks.txt** :
  - `libmpv-android f48aa82b2677428f756a689a44c2477c3f37be51`
- **Android / .kovelyx-gitlinks.txt** :
  - `MPVKit bfeb6fcfa2bd5081a9666ae5bb42b50e6c1537e8`
  - `libass-android c10b71ab8b4d90796d4a795f775d337c29198ad0`
- **Windows / .kovelyx-gitlinks.txt** :
  - `MPVKit bb1d0250ddcfa9d220761fcdad6011ac9fecf15e`
  - `libass-android c10b71ab8b4d90796d4a795f775d337c29198ad0`
- **Windows / .kovelyx-binaries-a-sauvegarder.txt** :
  - `07c68bb211f23a218ded0a36eb12207dc3aeb44e5318ffca6ce9dcc7c3173906 | 115320296 | apps/desktop/composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll`

## Limites

- Un hôte présent n'est pas nécessairement un appel actif à l'exécution.
- Les URLs construites à l'exécution, les données téléchargées, les secrets et services Docker nécessitent un audit séparé.
- Les références à Nuvio doivent être remplacées au bon niveau : ne pas les supprimer aveuglément.
- Les mises à jour des sources nécessitent de réexécuter l'audit.
