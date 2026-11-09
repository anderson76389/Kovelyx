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


## Protocole final de stabilité sur TV physique (version 1.1.1076 ou supérieure)

Ce contrôle **ne peut pas être validé par GitHub Actions** : il exige une vraie TV,
un téléviseur/boîtier et un accès ADB autorisé. Ne pas désinstaller Kovelyx,
vider les données, modifier la clé de signature ou envoyer des journaux privés.

1. **Avant le test** : relever le modèle, la RAM et `versionName`.
   Sur un PC connecté à la TV par ADB, noter le PSS :
   `adb shell dumpsys meminfo com.kovelyx.tv.debug`.
2. **15 minutes de télécommande** : parcourir Recherche, Accueil, Bibliothèque,
   ouvrir/fermer des fiches, revenir avec Retour/HOME et vérifier le focus DPAD.
   Effectuer trois cycles veille/réveil avec la télécommande du boîtier.
3. **20 à 30 minutes de vidéo autorisée** : tester H.264 puis HEVC/4K si matériel
   et fichiers compatibles, pause/reprise, cinq recherches avant/arrière, trois
   sorties et relances du lecteur, changement de langue audio et sous-titres FR.
   Noter les erreurs visibles plutôt que conclure qu'un codec marche sans l'avoir lu.
4. **À 0/15/30/45 minutes** : reprendre `dumpsys meminfo` et, si possible,
   `adb shell dumpsys gfxinfo com.kovelyx.tv.debug framestats`.
   La variation du PSS n'est pas à elle seule une fuite ; comparer après retour
   au même écran et au même état, rechercher une hausse répétée sans plateau.
5. **Après usage** : lancer `scripts/tv/diagnostic-media3-tv.ps1` sur le PC
   pour recueillir un journal local, puis vérifier absence d'ANR, crash,
   décalage audio, écran noir persistant et lenteur progressive.

Le workflow CI `.github/workflows/tv-e2e-emulator-pr.yml` ajoute un test
de **96 cycles DPAD / HOME / veille-réveil sur Google TV à 2 Go simulés**,
avec échantillons PSS et `gfxinfo`. La suite Media3 native ajoute
**six décodages H.264 avec libération du lecteur à chaque itération**.
Ce sont des contrôles complémentaires et non un substitut matériel.
