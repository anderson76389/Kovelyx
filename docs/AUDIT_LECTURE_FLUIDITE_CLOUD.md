# Audit Lecture, fluidité et synchronisation — 3 novembre 2026

Cet audit distingue le raccordement dans le code, les tests automatisés et la validation sur matériel réel. Une référence à un réglage ne prouve pas à elle seule son efficacité sur tous les appareils. Les hertz décrivent l’écran ; une vidéo 24 i/s reste une vidéo 24 i/s.

## Défauts corrigés

- Premiumize retiré des registres actifs des trois applications et du registre API mobile/PC. Les anciens champs de stockage et les implémentations internes restent pour éviter une migration destructive ; ils ne donnent plus accès au service dans l’application.
- APK publics : optimisation R8 et ressources, débogage désactivé, identifiant `.debug` historique et certificat permanent conservés. Les APK de développement/test ordinaires conservent leur configuration.
- Adaptation automatique de cadence TV : rétablie dans Lecture, vote temporaire coordonné avec le contrôleur de fréquence et limité à la plage min/max. La fin de lecture retire le vote temporaire sans changer les réglages sauvegardés.
- Windows vidéo : présentation synchronisée sur l’écran (display-resample), sans interpolation ; repli automatique mpv selon le flux/driver. Aucun gain matériel chiffré n’est annoncé.
- AutoSync automatique Android : commandes retirées des réglages Windows car ces préférences ne sont pas consommées par le lecteur natif ; le réglage manuel par lignes de sous-titres reste disponible dans le lecteur Windows.
- Fonds dégradés mobile/PC : brosses mises en cache entre les dessins, sans réduction de résolution ni changement de couleurs.
- Bibliothèque cloud : affiches et URL d’extension avec leur casse et leur requête ; fond, logo, description, genres, année, note et format transférés. Enrichissement à date égale sans écraser les métadonnées d’un autre appareil. Les informations perdues antérieurement ne peuvent réapparaître que si une copie locale les possède encore.

## Vérification fonctionnelle par famille

| Famille | Parcours vérifié | Limites |
|---|---|---|
| Lecteur interne/externe | Choix sauvegardé, sélection du moteur et lancement externe | Une application externe gère ses propres fonctions et doit être installée |
| Superpositions et commandes | Chargement, statut, pause, contrôle parental, gestes et appui accéléré | Gestes mobile ; commandes différentes dans le lecteur natif Windows |
| Audio et sous-titres | Langues, priorité, forcés, SDH, styles et piste sélectionnée | Traduction fournie par les sources ; formats/rendu dépendent du moteur |
| Synchronisation des sous-titres | AutoSync, tolérance et mode | Automatisation Android uniquement, dépendances natives et langue sélectionnée ; ne signifie pas synchronisation cloud |
| Sélection de flux | Réutilisation du lien, durée de cache, automatique, regex, filtres d’extensions/modules, délai | Liens expirables, fournisseurs disponibles et regex valides |
| Épisodes | Suivant, seuil, repli, groupe de visionnage, préchargement | Épisode suivant identifiable et flux disponible |
| Intro/générique | Marqueurs, sélection de segments, AnimeSkip/IntroDB | Identifiants et données de service nécessaires ; absence de marqueur n’est pas une panne du lecteur |
| Android Media3 | Priorité décodeur, tunneling, DV7, mémoire native, buffers et cache VOD | Décodeurs, codec et matériel compatibles ; options conditionnées au moteur |
| Android libmpv | Sortie vidéo, décodage matériel, YUV et pistes | Les réglages Media3 ne pilotent pas libmpv |
| TV audio avancé | Mixage, canaux, passthrough, amplification, délai, silence | Amplificateur, HDMI/optique et format audio compatibles |
| TV HDR/Dolby Vision | DV7/DV8.1, libdovi, HDR10+, modes matériels | Ne rend pas un écran SDR compatible HDR ; certains modes imposent un redémarrage de lecture |
| TV cadence/résolution | Adaptation rétablie avec bornes de fréquence | Modes réellement proposés par le téléviseur ; un changement HDMI peut provoquer un écran noir |
| Windows RTX | Interrupteur relié au moteur natif ; repli standard | GPU/driver compatibles ; activation du réglage ne prouve pas que RTX est utilisé |
| P2P | Consentement, profil, cache, taille, effacement, statistiques | Source torrent compatible et espace disque ; pas une extension cloud |
| Réseau et buffers | Valeurs sauvegardées et transmises à leur moteur | Un grand buffer ne corrige pas un débit insuffisant et peut augmenter la mémoire |

## Cloud : ce qui se synchronise et ses frontières

| Données | Mécanisme et protections | Frontière réelle |
|---|---|---|
| Extensions Stremio | URL complète, ordre, nom, activation, versions/conflits, reprise | Même compte/profil ; l’extension reste responsable de son service et de ses liens |
| Modules complémentaires | Dépôts/configuration, téléchargement validé avant remplacement | Code compatible avec la plateforme requis ; un module Android ne devient pas un module Windows |
| Collections et catalogues | Définitions complètes et disposition, fragments immuables, hash, CAS, fusion et suppressions | Format compatible, snapshot maximal 4 Mo ; importer un site web arbitraire n’est pas pris en charge |
| Profils | UUID stable, noms, images par profil, édition PC/mobile et réception TV | JPEG/PNG/WebP/GIF reconnus, maximum 18 Mo et 32 MP ; aucune conversion automatique des fichiers non reconnus |
| Images originales | Contrôle de signature, digest, cache privé, reprise et conflits | Original conservé à l’import/transfert ; affichage adapté à la définition de l’écran |
| Bibliothèque | Ajouts/suppressions, dates, révisions, métadonnées enrichies | N’inclut pas les abonnements ni les identifiants privés des services de suivi |
| Continuer à regarder | Positions, épisodes, vus et suppressions | Horloges des appareils cohérentes ; lien de lecture encore disponible |
| Déconnexion/reconnexion | Données cloud conservées, propriétaire/profil vérifiés | Une panne réseau peut retarder la réception ; ne pas annoncer une synchronisation parfaite sans essai de sessions réelles |
| Réglages de hertz | Locaux à chaque appareil | Volontairement non synchronisés entre écrans différents |

## Inventaire des réglages et points de consommation

Inventaire généré depuis les modèles et leurs références hors écran de réglages/stockage/tests. Les fichiers cités fournissent des points de départ pour la revue, pas une certification matérielle. Les options iOS présentes dans les modèles partagés ne concernent pas les trois distributions demandées.

### mobile — 75 champs du modèle

| Réglage | Consommation dans les sources de la cible |
|---|---|
| `useLegacyPlayerLayout` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `showLoadingOverlay` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `showPlayerLoadingStatus` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `pauseOverlayEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `showParentalGuide` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimePlaybackActions.kt` |
| `resizeMode` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeState.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenContent.kt` |
| `holdToSpeedEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeGestureActions.kt` |
| `holdToSpeedValue` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeGestureActions.kt` |
| `touchGesturesEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `externalPlayerEnabled` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt`; `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `externalPlayerForwardSubtitles` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `externalPlayerSendSkipSegments` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `externalPlayerId` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `preferredAudioLanguage` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeAudioPreferences.kt` |
| `secondaryPreferredAudioLanguage` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeAudioPreferences.kt` |
| `preferredSubtitleLanguage` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt` |
| `secondaryPreferredSubtitleLanguage` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt` |
| `subtitleStyle` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeTrackActions.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerTrackSelection.kt` |
| `streamReuseLastLinkEnabled` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt` |
| `streamReuseLastLinkCacheHours` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt` |
| `androidPlaybackEngine` | `commonMain/kotlin/com/nuvio/app/features/servers/ServerPlayback.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `androidLibmpvVideoOutput` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `androidLibmpvHardwareDecodingEnabled` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `androidLibmpvYuv420pEnabled` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `decoderPriority` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `mapDV7ToHevc` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `tunnelingEnabled` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `exoNativeMemoryEnabled` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `bufferEngineEnabled` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `minBufferMs` | `androidMain/kotlin/com/nuvio/app/features/player/NuvioExoPlayerPerformanceHelper.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `maxBufferMs` | `androidMain/kotlin/com/nuvio/app/features/player/NuvioExoPlayerPerformanceHelper.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `bufferForPlaybackMs` | `androidMain/kotlin/com/nuvio/app/features/player/NuvioExoPlayerPerformanceHelper.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `bufferForPlaybackAfterRebufferMs` | `androidMain/kotlin/com/nuvio/app/features/player/NuvioExoPlayerPerformanceHelper.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `backBufferDurationMs` | `androidMain/kotlin/com/nuvio/app/features/player/NuvioExoPlayerPerformanceHelper.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `targetBufferSizeMb` | `androidMain/kotlin/com/nuvio/app/features/player/NuvioExoPlayerPerformanceHelper.kt`; `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `vodCacheEnabled` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `vodCacheSizeMode` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `vodCacheSizeMb` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `streamAutoPlayMode` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayLoadingPolicy.kt` |
| `streamAutoPlaySource` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/debrid/DirectDebridStreamPreparer.kt` |
| `streamAutoPlaySelectedAddons` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/debrid/DirectDebridStreamPreparer.kt` |
| `streamAutoPlaySelectedPlugins` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/debrid/DirectDebridStreamPreparer.kt` |
| `streamAutoPlayRegex` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt` |
| `streamAutoPlayTimeoutSeconds` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `skipIntroEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `autoSkipSegmentTypes` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt` |
| `animeSkipEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `animeSkipClientId` | `commonMain/kotlin/com/nuvio/app/core/sync/ProviderCredentialSync.kt`; `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `introDbApiKey` | `commonMain/kotlin/com/nuvio/app/core/sync/ProviderCredentialSync.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `introSubmitEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt`; `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `streamAutoPlayNextEpisodeEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeSourceActions.kt` |
| `streamAutoPlayNextEpisodeFallbackEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `streamAutoPlayPreferBingeGroup` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt` |
| `streamAutoPlayReuseBingeGroup` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt` |
| `nextEpisodeThresholdMode` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `nextEpisodeThresholdPercent` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `nextEpisodeThresholdMinutesBeforeEnd` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `preloadNextEpisodeSources` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `useLibass` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `libassRenderType` | `androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt` |
| `iosVideoOutputPreset` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosToneMappingMode` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosTargetPrimaries` | iOS uniquement, hors cible |
| `iosTargetTransfer` | iOS uniquement, hors cible |
| `iosHardwareDecoderMode` | iOS uniquement, hors cible |
| `iosAudioOutputMode` | iOS uniquement, hors cible |
| `iosExtendedDynamicRangeEnabled` | iOS uniquement, hors cible |
| `iosTargetColorspaceHintEnabled` | iOS uniquement, hors cible |
| `iosHdrComputePeakEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosDebandEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosInterpolationEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosBrightness` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosContrast` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosSaturation` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosGamma` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |

### desktop — 76 champs du modèle

| Réglage | Consommation dans les sources de la cible |
|---|---|
| `useLegacyPlayerLayout` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `showLoadingOverlay` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `showPlayerLoadingStatus` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `pauseOverlayEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `showParentalGuide` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimePlaybackActions.kt` |
| `resizeMode` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeState.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenContent.kt` |
| `holdToSpeedEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeGestureActions.kt` |
| `holdToSpeedValue` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeGestureActions.kt` |
| `touchGesturesEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `externalPlayerEnabled` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt`; `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `externalPlayerForwardSubtitles` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `externalPlayerSendSkipSegments` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `externalPlayerId` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt` |
| `preferredAudioLanguage` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeAudioPreferences.kt` |
| `secondaryPreferredAudioLanguage` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeAudioPreferences.kt` |
| `preferredSubtitleLanguage` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt` |
| `secondaryPreferredSubtitleLanguage` | `commonMain/kotlin/com/nuvio/app/MainAppContent.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt` |
| `subtitleStyle` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeTrackActions.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerTrackSelection.kt` |
| `streamReuseLastLinkEnabled` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt` |
| `streamReuseLastLinkCacheHours` | `commonMain/kotlin/com/nuvio/app/StreamDestination.kt` |
| `androidPlaybackEngine` | `commonMain/kotlin/com/nuvio/app/features/servers/ServerPlayback.kt` |
| `androidLibmpvVideoOutput` | Option Android masquée sur Windows, hors moteur natif |
| `androidLibmpvHardwareDecodingEnabled` | Option Android masquée sur Windows, hors moteur natif |
| `androidLibmpvYuv420pEnabled` | Option Android masquée sur Windows, hors moteur natif |
| `decoderPriority` | `desktopMain/kotlin/com/nuvio/app/features/player/PlayerEngine.desktop.kt`; `desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerController.kt` |
| `mapDV7ToHevc` | Option Android masquée sur Windows, hors moteur natif |
| `tunnelingEnabled` | Option Android masquée sur Windows, hors moteur natif |
| `exoNativeMemoryEnabled` | Option Android masquée sur Windows, hors moteur natif |
| `bufferEngineEnabled` | Option Android masquée sur Windows, hors moteur natif |
| `minBufferMs` | Option Android masquée sur Windows, hors moteur natif |
| `maxBufferMs` | Option Android masquée sur Windows, hors moteur natif |
| `bufferForPlaybackMs` | Option Android masquée sur Windows, hors moteur natif |
| `bufferForPlaybackAfterRebufferMs` | Option Android masquée sur Windows, hors moteur natif |
| `backBufferDurationMs` | Option Android masquée sur Windows, hors moteur natif |
| `targetBufferSizeMb` | Option Android masquée sur Windows, hors moteur natif |
| `vodCacheEnabled` | Option Android masquée sur Windows, hors moteur natif |
| `vodCacheSizeMode` | Option Android masquée sur Windows, hors moteur natif |
| `vodCacheSizeMb` | Option Android masquée sur Windows, hors moteur natif |
| `streamAutoPlayMode` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayLoadingPolicy.kt` |
| `streamAutoPlaySource` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/debrid/DirectDebridStreamPreparer.kt` |
| `streamAutoPlaySelectedAddons` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/debrid/DirectDebridStreamPreparer.kt` |
| `streamAutoPlaySelectedPlugins` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/debrid/DirectDebridStreamPreparer.kt` |
| `streamAutoPlayRegex` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt` |
| `streamAutoPlayTimeoutSeconds` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `skipIntroEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `autoSkipSegmentTypes` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt` |
| `animeSkipEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `animeSkipClientId` | `commonMain/kotlin/com/nuvio/app/core/sync/ProviderCredentialSync.kt`; `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `introDbApiKey` | `commonMain/kotlin/com/nuvio/app/core/sync/ProviderCredentialSync.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `introSubmitEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt`; `commonMain/kotlin/com/nuvio/app/features/player/skip/SkipIntroRepository.kt` |
| `streamAutoPlayNextEpisodeEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeSourceActions.kt` |
| `streamAutoPlayNextEpisodeFallbackEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `streamAutoPlayPreferBingeGroup` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt` |
| `streamAutoPlayReuseBingeGroup` | `commonMain/kotlin/com/nuvio/app/features/streams/StreamAutoPlayPolicy.kt`; `commonMain/kotlin/com/nuvio/app/features/streams/StreamsRepository.kt` |
| `nextEpisodeThresholdMode` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `nextEpisodeThresholdPercent` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `nextEpisodeThresholdMinutesBeforeEnd` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `preloadNextEpisodeSources` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerNextEpisodeAutoPlay.kt` |
| `useLibass` | `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt`; `commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt` |
| `libassRenderType` | Option Android masquée sur Windows, hors moteur natif |
| `iosVideoOutputPreset` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosToneMappingMode` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosTargetPrimaries` | iOS uniquement, hors cible |
| `iosTargetTransfer` | iOS uniquement, hors cible |
| `iosHardwareDecoderMode` | iOS uniquement, hors cible |
| `iosAudioOutputMode` | iOS uniquement, hors cible |
| `iosExtendedDynamicRangeEnabled` | iOS uniquement, hors cible |
| `iosTargetColorspaceHintEnabled` | iOS uniquement, hors cible |
| `iosHdrComputePeakEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosDebandEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosInterpolationEnabled` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosBrightness` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosContrast` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosSaturation` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `iosGamma` | `commonMain/kotlin/com/nuvio/app/features/player/IosVideoSettingsModal.kt` |
| `nvidiaRtxSuperResolutionEnabled` | `desktopMain/kotlin/com/nuvio/app/features/player/PlayerEngine.desktop.kt`; `desktopMain/kotlin/com/nuvio/app/features/player/desktop/NativePlayerController.kt` |

### tv — 74 champs du modèle

| Réglage | Consommation dans les sources de la cible |
|---|---|
| `playerPreference` | `com/nuvio/tv/ui/screens/library/LibraryViewModel.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreen.kt` |
| `internalPlayerEngine` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `autoSwitchInternalPlayerOnError` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `useLibass` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `libassRenderType` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `subtitleStyle` | `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
| `bufferSettings` | `com/nuvio/tv/core/player/TrailerPlayerPool.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
| `decoderPriority` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerAudioDelayRoutes.kt` |
| `downmixEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `audioOutputChannels` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerAudioDelayRoutes.kt` |
| `maintainOriginalAudioOnDownmix` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerAudioDelayRoutes.kt` |
| `tunnelingEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerErrorRecovery.kt` |
| `forceOpticalPassthrough` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerAudioDelayRoutes.kt` |
| `skipSilence` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `audioAmplificationDb` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerAudioDelayRoutes.kt` |
| `centerMixLevelDb` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `persistAudioAmplification` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `rememberAudioDelayPerDevice` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `preferredAudioLanguage` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMetadata.kt` |
| `secondaryPreferredAudioLanguage` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMetadata.kt` |
| `loadingOverlayEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTracks.kt` |
| `showPlayerLoadingStatus` | `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `playbackIssueReportsEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `pauseOverlayEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `osdClockEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt`; `com/nuvio/tv/ui/screens/player/PlayerScreen.kt` |
| `skipIntroEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `parentalGuideEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `autoSkipSegmentTypes` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `dv5ToDv81Enabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `dv7ToDv81PreserveMappingEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `dv7HandlingMode` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `dv7LibdoviModeOverride` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `stripHdr10PlusSei` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `mpvHi10pGnextSoftwareFallbackEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
| `mpvHardwareDecodeMode` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `frameRateMatchingMode` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt` |
| `resolutionMatchingEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt` |
| `streamAutoPlayMode` | `com/nuvio/tv/core/debrid/DirectDebridStreamPreparer.kt`; `com/nuvio/tv/core/player/StreamAutoPlayPolicy.kt` |
| `streamAutoPlaySource` | `com/nuvio/tv/core/debrid/DirectDebridStreamPreparer.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `streamAutoPlaySelectedAddons` | `com/nuvio/tv/core/debrid/DirectDebridStreamPreparer.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `streamAutoPlaySelectedPlugins` | `com/nuvio/tv/core/debrid/DirectDebridStreamPreparer.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `streamAutoPlayRegex` | `com/nuvio/tv/core/debrid/DirectDebridStreamPreparer.kt`; `com/nuvio/tv/core/player/StreamAutoPlayPolicy.kt` |
| `postPlayRecommendationsEnabled` | `com/nuvio/tv/ui/screens/player/PostPlayRecommendationController.kt` |
| `postPlayMovieThresholdPercent` | `com/nuvio/tv/ui/screens/player/PostPlayRecommendationController.kt` |
| `streamAutoPlayNextEpisodeEnabled` | `com/nuvio/tv/core/player/ExternalPlaybackTracker.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt` |
| `streamAutoPlayNextEpisodeFallbackEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt` |
| `streamAutoPlayPreferBingeGroupForNextEpisode` | `com/nuvio/tv/core/player/StreamAutoPlayPolicy.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `streamAutoPlayReuseBingeGroup` | `com/nuvio/tv/core/player/StreamAutoPlayPolicy.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `streamAutoPlayTimeoutSeconds` | `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt` |
| `preloadNextEpisodeSources` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `stillWatchingEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `stillWatchingEpisodeThreshold` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `nextEpisodeThresholdMode` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `nextEpisodeThresholdPercent` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `nextEpisodeThresholdMinutesBeforeEnd` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `streamReuseLastLinkEnabled` | `com/nuvio/tv/core/player/StreamAutoPlayPolicy.kt`; `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `streamReuseLastLinkCacheHours` | `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `externalPlayerForwardSubtitles` | `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `externalPlayerSendSkipSegments` | `com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt` |
| `subtitleOrganizationMode` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `bufferEngineEnabled` | `com/nuvio/tv/core/player/TrailerPlayerPool.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
| `parallelNetworkEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt` |
| `bufferBudgetManaged` | `com/nuvio/tv/core/player/TrailerPlayerPool.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
| `allowLargeTargetBuffer` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt` |
| `vodCacheEnabled` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `vodCacheSizeMode` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `vodCacheSizeMb` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `useParallelConnections` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt` |
| `parallelConnectionCount` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt` |
| `parallelChunkSizeKb` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`; `com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt` |
| `enableHttp2` | `com/nuvio/tv/ui/screens/player/NuvioExoPlayerPerformanceHelper.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt` |
| `enableBufferLogs` | `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt` |
| `resizeMode` | `com/nuvio/tv/ui/components/TrailerPlayer.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
| `nuvioPerformanceModeEnabled` | `com/nuvio/tv/core/player/TrailerPlayerPool.kt`; `com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt` |
## Validation

Les résultats CI exacts et les identifiants de compilation sont consignés dans la PR #75 après validation. Les tests de politique de fréquence vérifient le vote temporaire et les bornes ; les tests de métadonnées vérifient la conservation de liens configurés et l’enrichissement idempotent. Les suites existantes couvrent sélection des pistes, sous-titres, lecture, épisodes, catalogues, extensions et images. Les essais physiques multiappareil et les comptes privés ne sont pas remplacés par ces tests.

Références officielles : https://developer.android.com/topic/performance/app-optimization/enable-app-optimization ; https://developer.android.com/media/optimize/performance/frame-rate .
