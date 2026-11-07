# Audit statique des références réseau

Rapport **indicatif** ; il ne certifie ni la compilation ni l'indépendance. Certaines URLs peuvent appartenir aux documents, aux tests ou être construites dynamiquement.

## TV

Fichiers texte examinés : **1385** ; références statiques à Nuvio/NuvioMedia : **17**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 230 |
| `www.themoviedb.org` | 131 |
| `media.example.com` | 48 |
| `trakt.tv` | 39 |
| `cdn.example` | 36 |
| `h` | 34 |
| `www.imdb.com` | 31 |
| `cdn.example.com` | 28 |
| `backend.example.com` | 27 |
| `...` | 26 |
| `addon.example` | 26 |
| `www.youtube.com` | 24 |
| `.` | 20 |
| `www.apache.org` | 19 |
| `127.0.0.1` | 16 |
| `cdn` | 16 |
| `x` | 16 |
| `video.example.com` | 16 |
| `example.test` | 16 |
| `schemas.android.com` | 15 |
| `media.trakt.tv` | 14 |
| `image` | 14 |
| `simkl.com` | 13 |
| `api.aioratings.com` | 13 |
| `github.com` | 11 |
| `image.tmdb.org` | 10 |
| `localhost` | 10 |
| `mdblist.com` | 9 |
| `192.168.1.10` | 9 |
| `plugins.example` | 9 |

### Références à vérifier en priorité

- `apps/tv/app/src/main/java/com/kovelyx/tv/data/remote/ServerDiscovery.kt:123`
- `apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:29`
- `apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:37`
- `apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:53`
- `apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:61`
- `apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:69`
- `apps/tv/app/src/test/java/com/kovelyx/tv/core/deeplink/DeepLinkParserTest.kt:75`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:135`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:136`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:141`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:142`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:149`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:150`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:153`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:158`
- `apps/tv/app/src/test/java/com/kovelyx/tv/data/remote/ServerDiscoveryPolicyTest.kt:160`
- `apps/tv/scripts/release_notes/rewrites.py:117`

## Android

Fichiers texte examinés : **1446** ; références statiques à Nuvio/NuvioMedia : **23**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 185 |
| `github.com` | 117 |
| `www.themoviedb.org` | 110 |
| `example.test` | 67 |
| `schemas.android.com` | 48 |
| `media.example.com` | 37 |
| `www.imdb.com` | 25 |
| `.` | 21 |
| `original.com` | 20 |
| `mdblist.com` | 18 |
| `api.trakt.tv` | 16 |
| `api.aioratings.com` | 13 |
| `backend.example.com` | 13 |
| `plugins.example` | 12 |
| `api.ratingposterdb.com` | 12 |
| `cdn.example.com` | 12 |
| `simkl.com` | 12 |
| `media.trakt.tv` | 12 |
| `postersplus.example.com` | 9 |
| `img` | 9 |
| `pm` | 9 |
| `api.mdblist.com` | 8 |
| `trakt.tv` | 8 |
| `catalog.example` | 8 |
| `addon.example` | 7 |
| `cdn.example` | 6 |
| `www.youtube.com` | 6 |
| `a.com` | 5 |
| `media.example.test` | 5 |
| `192.168.1.10` | 5 |

### Références à vérifier en priorité

- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:25`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:33`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:49`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:57`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:65`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:71`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:13`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:14`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:15`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:18`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:19`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:29`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:30`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:153`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:158`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:46`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:61`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:64`
- `apps/mobile/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:68`
- `apps/mobile/composeApp/src/iosMain/kotlin/com/kovelyx/app/features/player/ExternalPlayerPlatform.ios.kt:106`
- `apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/network/ServerDiscovery.kt:108`
- `apps/mobile/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/network/ServerDiscovery.kt:109`
- `apps/mobile/iosApp/DownloadsWidgetExtension/DownloadsLiveActivityWidget.swift:19`

## Windows

Fichiers texte examinés : **1832** ; références statiques à Nuvio/NuvioMedia : **22**.

| Hôte extrait | Occurrences |
| --- | ---: |
| `example.com` | 183 |
| `github.com` | 132 |
| `www.themoviedb.org` | 110 |
| `example.test` | 80 |
| `schemas.android.com` | 50 |
| `media.example.com` | 37 |
| `www.imdb.com` | 25 |
| `.` | 21 |
| `original.com` | 20 |
| `mdblist.com` | 18 |
| `simkl.com` | 16 |
| `api.trakt.tv` | 16 |
| `api.ratingposterdb.com` | 14 |
| `api.aioratings.com` | 13 |
| `backend.example.com` | 13 |
| `plugins.example` | 12 |
| `cdn.example.com` | 12 |
| `media.trakt.tv` | 12 |
| `example.invalid` | 11 |
| `trakt.tv` | 10 |
| `postersplus.example.com` | 9 |
| `img` | 9 |
| `image.tmdb.org` | 9 |
| `pm` | 9 |
| `example.org` | 8 |
| `api.mdblist.com` | 8 |
| `catalog.example` | 8 |
| `addon.example` | 7 |
| `media.w3.org` | 7 |
| `anderson76389.github.io` | 6 |

### Références à vérifier en priorité

- `apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/DesktopCollectionImportBridge.kt:236`
- `apps/desktop/composeApp/src/desktopMain/kotlin/com/kovelyx/app/Main.kt:384`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:25`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:33`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:49`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:57`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:65`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/deeplink/AppUrlBridgeTest.kt:71`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:13`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:14`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:17`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:25`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/core/network/ServerDiscoveryPolicyTest.kt:27`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:153`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/player/InfusePlaybackTest.kt:158`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:46`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:61`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:64`
- `apps/desktop/composeApp/src/commonTest/kotlin/com/kovelyx/app/features/simkl/SimklPkceTest.kt:68`
- `apps/desktop/composeApp/src/iosMain/kotlin/com/kovelyx/app/features/player/ExternalPlayerPlatform.ios.kt:106`
- `apps/desktop/composeApp/src/commonMain/kotlin/com/kovelyx/app/core/network/ServerDiscovery.kt:105`
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
