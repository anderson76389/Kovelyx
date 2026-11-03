<div align="center">

<img src="assets/brand/app_logo_wordmark.png" alt="Kovelyx TV" width="300">

# Kovelyx TV — Android TV et TV Box

Une interface multimédia conçue pour la **télécommande**, les téléviseurs
Android TV, Google TV et les boîtiers compatibles Android 7+.

**Préversion de développement.** Le compte et la synchronisation Kovelyx utilisent
le Worker Cloudflare configuré à la compilation. Les essais entre appareils
physiques et la lecture sur les différents modèles de TV restent à valider.

[**🏠 Dépôt Kovelyx**](https://github.com/anderson76389/Kovelyx) ·
[**📦 Releases**](https://github.com/anderson76389/Kovelyx/releases) ·
[**🛠️ Builds TV**](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)

</div>

## Où télécharger ?

Les APK TV signés sont disponibles dans les [Releases Kovelyx](https://github.com/anderson76389/Kovelyx/releases).
Choisir le fichier `Kovelyx-TV-v…-universal.apk`, compatible avec les différentes architectures TV.
Les préversions sont proposées par la bannière de mise à jour sur le canal **Bêta**.
Les trois Releases les plus récentes sont conservées ; les anciennes adresses de téléchargement peuvent donc disparaître.

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
