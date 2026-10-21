# Kovelyx Media3 — validation sur une vraie Android TV

## Ce qui est et n'est pas prouvé
- **Oui** : build Full Debug Android TV depuis le commit Media3, suite Kotlin TV, reconstruction contrôlée des AAR, installation et démarrage sur émulateur Google TV.
- **Non** : décodage / stabilité sur puce ARM, HDR, Dolby Vision, différentes TV Box, audio passthrough, et lecture réelle sur TV physique. La simulation CI ne remplace pas ces essais.

## Diagnostic sans risque de données
Un script Windows est inclus : [`scripts/tv/diagnostic-media3-tv.ps1`](../scripts/tv/diagnostic-media3-tv.ps1).
Il n'effectue **aucune désinstallation, installation, mise à jour, suppression de profils, connexion compte, réinitialisation des journaux ou modification des paramètres**.

1. Sur la TV, activer les options développeur et le débogage ADB/Wi-Fi uniquement pour le temps du test. L'appareil doit être sur un réseau de confiance.
2. Depuis le PC Windows, connecter la TV avec **Android SDK Platform-Tools** (ADB), par `adb pair` puis `adb connect` lorsque l'appareil l'exige.
3. Avec **Kovelyx TV Debug déjà installé**, exécuter le script PowerShell du dépôt `scripts/tv/diagnostic-media3-tv.ps1`. Il refuse de tester un appareil non TV ou absent.
4. Dans Kovelyx TV, lire une vidéo H.264/AVC autorisée, puis tester lecture, pause, reprise, recherche avant/arrière, lecture prolongée et sortie; refaire un essai avec HEVC et Dolby Vision si le matériel le prend en charge.
5. Valider le résultat dans PowerShell. Les journaux et `observations.txt` sont enregistrés **localement**; ils ne sont jamais téléversés automatiquement.
6. Examiner et masquer tout identifiant, URL privée ou secret avant de partager un rapport. Désactiver le débogage ADB sur la TV après diagnostic.

## Critères pour conclure
- Aucun crash, ANR ou écran noir persistant dans les scénarios lus.
- Pas de fuite mémoire native visible pendant les lectures et sorties répétées.
- Vidéo **et** audio continus, barre de recherche cohérente, décodage H.264 et HEVC sur les codecs réellement présents.
- Plusieurs cycles arrêt/reprise et changement de flux; ne pas conclure sur une seule vidéo.
- Ne pas déduire une stabilité matérielle d'un test JVM ou émulateur.

**Attention aux signatures Debug** : ne jamais désinstaller une APK existante pour contourner `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Une réinstallation peut effacer les données locales, notamment les profils. Aucun test ici ne nécessite ce changement.
