# Vérifier les paramètres de Kovelyx TV sur une Freebox Pop

Cette procédure exécute **sur le Player Freebox Pop** le même audit ADB non destructif que celui utilisé sur l'émulateur Google TV. Elle ne requiert ni root ni modification du firmware.

## Prérequis
- Un PC Windows et la Freebox Pop sur le même réseau local.
- Android SDK Platform-Tools (commande `adb`) et Python 3 sur le PC.
- L'APK publique de Kovelyx TV installée sur le Player.
- Le **débogage ADB activé et autorisé** sur le Player. Selon le firmware Free, le débogage via TCP peut être indisponible ; il ne faut pas supposer que le port 5555 est ouvert.

## Connexion

Dans les options développeur Android TV du Player, activer le débogage autorisé par le firmware et relever l'adresse IP de la Freebox Pop.

Depuis PowerShell sur le PC :

```powershell
adb connect IP_DE_LA_FREEBOX:5555
adb devices -l
```

Ne poursuivre que si `adb devices -l` affiche la box en état `device`, après avoir accepté la demande d'autorisation sur l'écran. Un refus, un `offline` ou une connexion impossible signifie qu'il faut configurer le débogage selon la version du firmware ; ces commandes ne contournent pas les protections Free.

## Audit de tous les menus de paramètres

Dans Kovelyx TV, ouvrir le profil principal puis **Paramètres** avant d'exécuter la commande suivante à la racine du dépôt Kovelyx :

```powershell
python scripts/tests/tv_settings_adb_probe.py --device IP_DE_LA_FREEBOX:5555 --package com.kovelyx.tv --settings-open --output .\freebox-pop-settings-report
```

Remplacer le nom du paquet uniquement si l'APK installée utilise une identité différente. La commande teste les catégories visibles en français ou en anglais, les mouvements DPAD, la stabilité du processus et la mémoire PSS. Les rubriques Compte/Profils peuvent être masquées pour un profil secondaire et Débogage est normalement absent des versions publiques.

Le fichier `freebox-pop-settings-report/report.json` contient les rubriques accessibles, les erreurs éventuelles et les mesures de mémoire. Aucune modification des options n'est effectuée : le programme ouvre les catégories sans activer ni désactiver les réglages. Il ne teste pas le fonctionnement réel de chaque connexion externe ni la lecture Dolby/4K.

**Confidentialité :** le rapport et la capture restent localement sur le PC ; inspecter les fichiers avant de les transmettre. Le programme n'envoie aucun diagnostic à un serveur.

Pour une validation approfondie des différents contrôles, réaliser ensuite des essais explicites sur le Player (lecture, sous-titres, paramètres de langue, mise à jour, bibliothèque et synchronisation), en sauvegardant les données avant de modifier un réglage sensible.
