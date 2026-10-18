# Audit statique des références réseau

Rapport **indicatif** ; il ne certifie ni la compilation ni l'indépendance. Certaines URLs peuvent appartenir aux documents, aux tests ou être construites dynamiquement.

## TV

Fichiers texte examinés : **1358** ; références statiques à Nuvio/NuvioMedia : **6776**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 226 |
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
| `postersplus.example.com` | 9 |
| `subs.example.com` | 8 |

### Références à vérifier en priorité

- `apps/tv/app/build.gradle.kts:100`
- `apps/tv/app/build.gradle.kts:374`
- `apps/tv/app/build.gradle.kts:406`
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
- `apps/tv/app/src/full/java/com/nuvio/tv/updater/ApkInstaller.kt:9`
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
- …et 6696 autres correspondances (plafond d'affichage).

## Android

Fichiers texte examinés : **1408** ; références statiques à Nuvio/NuvioMedia : **109**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `github.com` | 228 |
| `example.com` | 177 |
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

- `apps/mobile/store.json:5`
- `apps/mobile/store.json:6`
- `apps/mobile/store.json:15`
- `apps/mobile/store.json:18`
- `apps/mobile/store.json:26`
- `apps/mobile/store.json:27`
- `apps/mobile/store.json:36`
- `apps/mobile/store.json:37`
- `apps/mobile/store.json:46`
- `apps/mobile/store.json:47`
- `apps/mobile/store.json:56`
- `apps/mobile/store.json:57`
- `apps/mobile/store.json:66`
- `apps/mobile/store.json:67`
- `apps/mobile/store.json:76`
- `apps/mobile/store.json:77`
- `apps/mobile/store.json:86`
- `apps/mobile/store.json:87`
- `apps/mobile/store.json:96`
- `apps/mobile/store.json:97`
- `apps/mobile/store.json:106`
- `apps/mobile/store.json:107`
- `apps/mobile/store.json:116`
- `apps/mobile/store.json:117`
- `apps/mobile/store.json:126`
- `apps/mobile/store.json:127`
- `apps/mobile/store.json:136`
- `apps/mobile/store.json:137`
- `apps/mobile/store.json:147`
- `apps/mobile/store.json:156`
- `apps/mobile/store.json:157`
- `apps/mobile/store.json:166`
- `apps/mobile/store.json:167`
- `apps/mobile/store.json:176`
- `apps/mobile/store.json:177`
- `apps/mobile/store.json:187`
- `apps/mobile/store.json:197`
- `apps/mobile/store.json:206`
- `apps/mobile/store.json:207`
- `apps/mobile/store.json:216`
- `apps/mobile/store.json:217`
- `apps/mobile/store.json:226`
- `apps/mobile/store.json:227`
- `apps/mobile/store.json:236`
- `apps/mobile/store.json:237`
- `apps/mobile/store.json:246`
- `apps/mobile/store.json:247`
- `apps/mobile/store.json:256`
- `apps/mobile/store.json:257`
- `apps/mobile/store.json:266`
- `apps/mobile/store.json:267`
- `apps/mobile/store.json:276`
- `apps/mobile/store.json:277`
- `apps/mobile/scripts/prepare-ios-dependencies.sh:25`
- `apps/mobile/MPVKit/Package.swift:304`
- `apps/mobile/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`
- `apps/mobile/.github/ISSUE_TEMPLATE/config.yml:4`
- `apps/mobile/.github/ISSUE_TEMPLATE/config.yml:7`
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
- `apps/mobile/composeApp/src/commonMain/kotlin/com/nuvio/app/core/auth/DeviceLinkAuthRepository.kt:55`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:352`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:395`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-ru/strings.xml:396`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:358`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:401`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-bn/strings.xml:402`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1951`
- `apps/mobile/composeApp/src/commonMain/composeResources/values-cs/strings.xml:1983`
- …et 29 autres correspondances (plafond d'affichage).

## Windows

Fichiers texte examinés : **1785** ; références statiques à Nuvio/NuvioMedia : **119**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `github.com` | 246 |
| `example.com` | 178 |
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

- `apps/desktop/store.json:5`
- `apps/desktop/store.json:6`
- `apps/desktop/store.json:15`
- `apps/desktop/store.json:18`
- `apps/desktop/store.json:26`
- `apps/desktop/store.json:27`
- `apps/desktop/store.json:36`
- `apps/desktop/store.json:37`
- `apps/desktop/store.json:46`
- `apps/desktop/store.json:47`
- `apps/desktop/store.json:56`
- `apps/desktop/store.json:57`
- `apps/desktop/store.json:66`
- `apps/desktop/store.json:67`
- `apps/desktop/store.json:76`
- `apps/desktop/store.json:77`
- `apps/desktop/store.json:86`
- `apps/desktop/store.json:87`
- `apps/desktop/store.json:96`
- `apps/desktop/store.json:97`
- `apps/desktop/store.json:106`
- `apps/desktop/store.json:107`
- `apps/desktop/store.json:116`
- `apps/desktop/store.json:117`
- `apps/desktop/store.json:126`
- `apps/desktop/store.json:127`
- `apps/desktop/store.json:136`
- `apps/desktop/store.json:137`
- `apps/desktop/store.json:147`
- `apps/desktop/store.json:156`
- `apps/desktop/store.json:157`
- `apps/desktop/store.json:166`
- `apps/desktop/store.json:167`
- `apps/desktop/store.json:176`
- `apps/desktop/store.json:177`
- `apps/desktop/store.json:187`
- `apps/desktop/store.json:197`
- `apps/desktop/store.json:206`
- `apps/desktop/store.json:207`
- `apps/desktop/store.json:216`
- `apps/desktop/store.json:217`
- `apps/desktop/store.json:226`
- `apps/desktop/store.json:227`
- `apps/desktop/store.json:236`
- `apps/desktop/store.json:237`
- `apps/desktop/store.json:246`
- `apps/desktop/store.json:247`
- `apps/desktop/store.json:256`
- `apps/desktop/store.json:257`
- `apps/desktop/store.json:266`
- `apps/desktop/store.json:267`
- `apps/desktop/store.json:276`
- `apps/desktop/store.json:277`
- `apps/desktop/scripts/prepare-ios-dependencies.sh:25`
- `apps/desktop/MPVKit/Package.swift:304`
- `apps/desktop/composeApp/build.gradle.kts:144`
- `apps/desktop/composeApp/build.gradle.kts:158`
- `apps/desktop/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`
- `apps/desktop/.github/ISSUE_TEMPLATE/config.yml:4`
- `apps/desktop/.github/ISSUE_TEMPLATE/config.yml:7`
- `apps/desktop/.github/workflows/desktop-release.yml:941`
- `apps/desktop/scripts/linux/com.nuvio.media.desktop.metainfo.xml:17`
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
- `apps/desktop/composeApp/src/iosMain/kotlin/com/nuvio/app/features/updater/AppUpdaterPlatform.ios.kt:15`
- `apps/desktop/composeApp/src/commonMain/kotlin/com/nuvio/app/core/network/ServerDiscovery.kt:50`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:352`
- `apps/desktop/composeApp/src/commonMain/composeResources/values-ru/strings.xml:395`
- …et 39 autres correspondances (plafond d'affichage).

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
