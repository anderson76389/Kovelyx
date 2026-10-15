<div align="center">

<img src="assets/brand/app_logo_wordmark.png" alt="Kovelyx TV" width="300">

# Kovelyx TV — Android TV et TV Box

Une interface multimédia conçue pour la **télécommande**, les téléviseurs
Android TV, Google TV et les boîtiers compatibles Android 7+.

**Préversion de développement.** Les comptes Kovelyx indépendants et la
synchronisation multiplateforme seront intégrés plus tard, après la
stabilisation des applications.

[**🏠 Dépôt Kovelyx**](https://github.com/anderson76389/Kovelyx) ·
[**📦 Releases**](https://github.com/anderson76389/Kovelyx/releases) ·
[**🛠️ Builds TV**](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)

</div>

## Où télécharger ?

La première **préversion APK TV universelle** a été compilée, signée, installée et démarrée sur un émulateur Google TV, avec vérification des touches de navigation. Téléchargement direct :

[**📺 Télécharger Kovelyx TV (APK universel)**](https://github.com/anderson76389/Kovelyx/releases/download/tv-preview-9c6ec281a980/Kovelyx-TV-tv-preview-9c6ec281a980-universal.apk) · [Notes de version](https://github.com/anderson76389/Kovelyx/releases/tag/tv-preview-9c6ec281a980)

Ce résultat ne valide pas encore la lecture de tous les formats et les services externes sur une TV Box réelle.

**Ne pas installer l'APK Android téléphone en lieu et place de l'APK TV** :
ils ont des interfaces et des identifiants distincts.

## Ce qui fonctionne techniquement dans le code

- Navigation et écrans prévus pour la télécommande (Android TV Leanback)
- Bibliothèque, catalogues et fournisseurs de métadonnées configurables
- Modules de lecture et de sous-titres ; comportement dépendant des sources
- Extensions, profils et réglages locaux ; compte distant optionnel selon configuration
- Couleurs et marque Kovelyx ; prise en charge du schéma `kovelyx://`

L'inventaire n'est **pas** une garantie de fonctionnement de chaque fonction
sur une box réelle. Voir les journaux et essais dans
[GitHub Actions](https://github.com/anderson76389/Kovelyx/actions).

## Compiler

```bash
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx/apps/tv
./gradlew :app:assembleFullDebug --no-daemon
```

Prérequis : JDK 21 et Android SDK. Les fichiers APK sont générés dans
`app/build/outputs/apk/full/debug/` (dont une version `universal.apk`).

**Signature :** les builds Debug sont destinés uniquement aux essais. Ils
ne garantissent pas une mise à jour sur place sans désinstaller l'ancienne
application. Sauvegarder les données avant toute réinstallation.

## Origine et licences

Les sources, notices et licences obligatoires sont conservées dans le dépôt :
[provenance](../../docs/PROVENANCE.md) et [GPLv3](LICENSE).
Les mentions de licence n'impliquent aucune dépendance réseau à l'ancien projet.
