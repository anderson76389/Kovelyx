# Audit statique des références réseau

Rapport **indicatif** ; il ne certifie ni la compilation ni l'indépendance. Certaines URLs peuvent appartenir aux documents, aux tests ou être construites dynamiquement.

## TV

Fichiers texte examinés : **1363** ; références statiques à Nuvio/NuvioMedia : **6801**.

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
| `backend.example.com` | 26 |
| `...` | 26 |
| `www.youtube.com` | 24 |
| `addon.example` | 22 |
| `.` | 20 |
| `www.apache.org` | 19 |
| `127.0.0.1` | 16 |
| `video.example.com` | 16 |
| `x` | 16 |
| `cdn` | 16 |
| `schemas.android.com` | 15 |
| `image` | 14 |
| `media.trakt.tv` | 14 |
| `github.com` | 13 |
| `simkl.com` | 13 |
| `api.aioratings.com` | 13 |
| `example.test` | 12 |
| `localhost` | 10 |
| `image.tmdb.org` | 10 |
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
- `apps/tv/app/src/playstore/java/com/nuvio/tv/di/PluginModule.kt:1`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/di/PluginModule.kt:3`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/updater/UpdateViewModel.kt:1`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:1`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:3`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:4`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:5`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:6`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:1`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/updater/ui/UpdateBannerHost.kt:1`
- `apps/tv/app/src/playstore/java/com/nuvio/tv/updater/ui/UpdateBannerHost.kt:4`
- `apps/tv/app/src/testFull/java/com/nuvio/tv/core/plugin/FetchResponseHeadersTest.kt:1`
- `apps/tv/app/src/testFull/java/com/nuvio/tv/core/plugin/cloudstream/HomeViewModelCompatStubTest.kt:1`
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
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:6`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:7`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateViewModel.kt:8`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdatePreferences.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdatePreferences.kt:11`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkInstaller.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkInstaller.kt:11`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/AbiSelector.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/AbiSelector.kt:4`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:3`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:4`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/UpdateRepository.kt:5`
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkDownloader.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:4`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:5`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:6`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:7`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:8`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:9`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:10`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:11`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:12`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:13`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:14`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:15`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:16`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:17`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:66`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:67`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:79`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:421`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/FetchResponseHeaders.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:9`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:10`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:11`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:50`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:10`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:53`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/TvTypeExtensions.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:1`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:24`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:25`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:26`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:27`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:28`
- `apps/tv/app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:29`
- …et 6721 autres correspondances (plafond d'affichage).

## Android

Fichiers texte examinés : **1405** ; références statiques à Nuvio/NuvioMedia : **53**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 182 |
| `github.com` | 120 |
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
| `cdn.example.com` | 12 |
| `media.trakt.tv` | 12 |
| `simkl.com` | 12 |
| `api.ratingposterdb.com` | 12 |
| `pm` | 9 |
| `img` | 9 |
| `postersplus.example.com` | 9 |
| `api.mdblist.com` | 8 |
| `catalog.example` | 8 |
| `trakt.tv` | 7 |
| `www.youtube.com` | 6 |
| `192.168.1.10` | 5 |
| `a.com` | 5 |
| `fake.example` | 4 |
| `v3-cinemeta.strem.io` | 4 |
| `opensubtitles.example` | 4 |
| `image.tmdb.org` | 4 |

### Références à vérifier en priorité

- `apps/mobile/scripts/prepare-ios-dependencies.sh:25`
- `apps/mobile/MPVKit/Package.swift:304`
- `apps/mobile/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:153`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:158`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:46`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:61`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:64`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:68`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:25`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:33`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:49`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:57`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:65`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:71`
- `apps/mobile/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/ExternalPlayerPlatform.ios.kt:106`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:395`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:396`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:358`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:402`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1951`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1983`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1984`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2185`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2217`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2218`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nl/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nl/strings.xml:395`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nl/strings.xml:396`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bg/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-vi/strings.xml:357`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-vi/strings.xml:400`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-vi/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ur/strings.xml:357`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ur/strings.xml:400`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ur/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2042`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2074`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2075`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nb/strings.xml:511`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1273`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1274`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-es/strings.xml:355`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-es/strings.xml:398`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-es/strings.xml:399`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-hr/strings.xml:357`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-hr/strings.xml:400`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-hr/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-el/strings.xml:142`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-el/strings.xml:183`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-el/strings.xml:184`

## Windows

Fichiers texte examinés : **1781** ; références statiques à Nuvio/NuvioMedia : **59**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 181 |
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
| `pm` | 9 |
| `trakt.tv` | 9 |
| `img` | 9 |
| `image.tmdb.org` | 9 |
| `postersplus.example.com` | 9 |
| `api.mdblist.com` | 8 |
| `catalog.example` | 8 |
| `example.org` | 8 |
| `media.w3.org` | 7 |
| `anderson76389.github.io` | 7 |
| `developer.apple.com` | 6 |
| `www.youtube.com` | 6 |

### Références à vérifier en priorité

- `apps/desktop/scripts/prepare-ios-dependencies.sh:25`
- `apps/desktop/MPVKit/Package.swift:304`
- `apps/desktop/composeApp/build.gradle.kts:144`
- `apps/desktop/composeApp/build.gradle.kts:158`
- `apps/desktop/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`
- `apps/desktop/scripts/linux/test-linux-deb-tools.sh:30`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:153`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/player/InfusePlaybackTest.kt:158`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:46`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:61`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:64`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/features/simkl/SimklPkceTest.kt:68`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:25`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:33`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:49`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:57`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:65`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/nuvio/app/core/deeplink/AppUrlBridgeTest.kt:71`
- `apps/desktop/composeApp/src/iosMain/kotlin/com/nuvio/app/features/player/ExternalPlayerPlatform.ios.kt:106`
- `apps/desktop/composeApp/src/commonMain/kotlin/com/nuvio/app/core/network/ServerDiscovery.kt:50`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:395`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:396`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bn/strings.xml:358`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bn/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bn/strings.xml:402`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1952`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1984`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1985`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2186`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2218`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-sk/strings.xml:2219`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nl/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nl/strings.xml:395`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nl/strings.xml:396`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-bg/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-vi/strings.xml:357`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-vi/strings.xml:400`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-vi/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ur/strings.xml:357`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ur/strings.xml:400`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ur/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2045`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2077`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-pl/strings.xml:2078`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nb/strings.xml:511`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1273`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-nb/strings.xml:1274`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-es/strings.xml:355`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-es/strings.xml:398`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-es/strings.xml:399`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-hr/strings.xml:357`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-hr/strings.xml:400`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-hr/strings.xml:401`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-el/strings.xml:142`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-el/strings.xml:183`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-el/strings.xml:184`
- `apps/desktop/composeApp/src/desktopMain/kotlin/com/nuvio/app/DesktopCollectionImportBridge.kt:229`
- `apps/desktop/composeApp/src/desktopMain/kotlin/com/nuvio/app/Main.kt:350`

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
