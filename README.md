<div align="center">

<img src="./assets/brand/kovelyx-banner.svg" alt="Kovelyx — ton univers multimédia sur Windows et Android" width="100%" />

<br>

<img src="./assets/brand/kovelyx-wordmark.svg" alt="Kovelyx" width="300">

### Kovelyx pour Windows et Android. Ton univers multimédia.

**Trois applications en préversion : Windows 10/11 x64, Android mobile et Android TV / TV Box (Android 7+).** Elles sont regroupées dans **ce même dépôt**, avec des sources et téléchargements distincts. Les travaux Linux sont arrêtés.

[**⬇️ Télécharger Windows, Android et TV**](#-télécharger-kovelyx) · [**📦 GitHub Releases**](https://github.com/anderson76389/Kovelyx/releases) · [**🌐 Site officiel**](https://anderson76389.github.io/Kovelyx/) · [**📖 Guide PC**](./docs/PC.md)

[![Installer Kovelyx Windows](https://img.shields.io/badge/⬇_WINDOWS-Installer_EXE-754CE8?style=for-the-badge&logo=windows11&logoColor=white)](https://github.com/anderson76389/Kovelyx/releases/download/v0.1.309-alpha/Kovelyx-Windows-Setup-v0.1.309-alpha.exe)
[![APK Kovelyx Android](https://img.shields.io/badge/⬇_ANDROID-APK_universel-754CE8?style=for-the-badge&logo=android&logoColor=white)](https://github.com/anderson76389/Kovelyx/releases/tag/android-preview-0e43cbe05227)
[![APK Kovelyx Android TV](https://img.shields.io/badge/⬇_ANDROID_TV-APK_universel-754CE8?style=for-the-badge&logo=androidtv&logoColor=white)](https://github.com/anderson76389/Kovelyx/releases/tag/tv-preview-9c6ec281a980)
[![GPL--3.0](https://img.shields.io/badge/Licence-GPLv3-49435C?style=for-the-badge)](./docs/PROVENANCE.md)

[![Compilation Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)

</div>

---

## ✦ Pourquoi Kovelyx ?

Des films, des séries, des bibliothèques et des sources configurables dans une interface pensée pour le bureau. Notre priorité : **garder la maîtrise du code, des paramètres et des builds**, et proposer des fichiers simples à installer ou à lancer.

<table>
<tr>
<td align="center" width="33%"><strong>🎬 Découverte et lecture</strong><br><sub>Catalogue, recherche, lecteurs, pistes audio et sous-titres : fonctions à vérifier avec vos sources.</sub></td>
<td align="center" width="33%"><strong>🧩 Extensions et préférences</strong><br><sub>Configuration personnalisable, thèmes, extensions et suivi de lecture.</sub></td>
<td align="center" width="33%"><strong>🖼️ Profils personnels</strong><br><sub>Avatar et fond choisis sur ton ordinateur, copiés dans le stockage local Kovelyx.</sub></td>
</tr>
</table>

## ⬇️ Télécharger Kovelyx

**Trois téléchargements distincts : Windows, Android mobile et Android TV.** Il s’agit de **préversions expérimentales**, pas de versions stables. La TV a été démarrée sur émulateur avec navigation à la télécommande ; il reste à tester toutes les fonctions sur appareil.

<table>
<tr>
<td width="33%" valign="top"><h3>🪟 Windows 10 / 11 — x64</h3>
<p><strong>EXE installable, MSI ou ZIP portable.</strong></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/download/v0.1.309-alpha/Kovelyx-Windows-Setup-v0.1.309-alpha.exe"><strong>⬇️ Télécharger Windows (.exe)</strong></a></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.309-alpha">Release Windows</a></p></td>
<td width="33%" valign="top"><h3>📱 Android mobile — 7+</h3>
<p><strong>APK universel pour téléphone et tablette.</strong></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/download/android-preview-0e43cbe05227/Kovelyx-Android-android-preview-0e43cbe05227.apk"><strong>⬇️ Télécharger Android (.apk)</strong></a></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/tag/android-preview-0e43cbe05227">Release Android</a></p></td>
<td width="33%" valign="top"><h3>📺 Android TV / TV Box — 7+</h3>
<p><strong>APK universel adapté à la télécommande.</strong></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/download/tv-preview-9c6ec281a980/Kovelyx-TV-tv-preview-9c6ec281a980-universal.apk"><strong>⬇️ Télécharger TV (.apk)</strong></a></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/tag/tv-preview-9c6ec281a980">Release TV testée sur émulateur</a></p></td>
</tr>
</table>

> **Téléchargements Android :** les APK mobile et TV sont directement accessibles depuis leurs Releases respectives. Les builds Debug ne garantissent pas des mises à jour sans désinstallation : sauvegardez vos données.

**📦 [Voir toutes les GitHub Releases](https://github.com/anderson76389/Kovelyx/releases) · 🌐 [Site de téléchargement](https://anderson76389.github.io/Kovelyx/) · 🔧 [Débloquer la publication des APK](./docs/RELEASES.md)**

Windows possède également des formats secondaires **MSI** et **ZIP portable**, disponibles sur la [Release Windows](https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.309-alpha). Ils ne constituent pas de nouvelles plateformes. La version TV dispose de son propre APK publié. Les anciens fichiers Linux restent dans l'historique des anciennes Releases.

**Vérifications réelles :** Windows dispose d'un build automatisé réussi ; Android a passé la compilation, la vérification de signature et d'identifiant, l'installation et le démarrage sur émulateur Android 34. **Cela ne garantit pas** toutes les fonctions multimédias, les connexions ou la compatibilité de chaque appareil. Le client Android n'est pas encore entièrement identique au client Windows.

### 🪟 Installation Windows

1. Télécharge le **Setup.exe** ci-dessus depuis GitHub Releases (pas besoin de décompresser un ZIP).
2. Vérifie l'origine GitHub et le [SHA-256 publié](https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.309-alpha) avant de lancer le programme.
3. La version portable exige d'extraire l'intégralité du ZIP ; les bibliothèques et le runtime restent nécessaires.

### 📱 Installation Android et Android TV

1. Télécharge l’APK correspondant à ton appareil dans la Release ci-dessus.
2. Autorise l’installation depuis la source choisie sur le téléphone ou la TV Box, puis installe le fichier.
3. Sur télévision, utilise **Kovelyx-TV-…universal.apk** (interface télécommande). La version mobile est distincte.

**Versions expérimentales :** ne pas garantir toutes les fonctions, la lecture de tous les formats ni une migration des données entre APK Debug. Sauvegarde les données locales avant remplacement.

### 🔄 Mises à jour intégrées à Kovelyx

L'application vérifie les nouvelles **Releases GitHub** au démarrage et périodiquement lorsqu'elle reste ouverte. Quand une version compatible plus récente est publiée, une **bannière en haut** permet de télécharger le fichier avec une barre de progression. Le téléchargement est contrôlé par son empreinte SHA-256 avant l'installation.

- **Windows Setup.exe / MSI :** l'application télécharge le nouvel installateur et le lance après votre clic, sans passer manuellement par le site.
- **Windows portable :** le ZIP complet est téléchargé depuis Kovelyx, vérifié puis remplacé après fermeture, avec sauvegarde de l'ancienne copie pour revenir en arrière en cas de problème.
- Les versions non compatibles ou non publiées ne sont pas proposées.

**État des APK Android :** les premières préversions mobile et TV sont publiées dans GitHub Releases. Les builds suivants ne sont publiés que sous réserve des contrôles prévus. Les signatures Debug peuvent changer, donc une mise à jour sans désinstallation n’est pas garantie.

**Important :** ce mécanisme évite les téléchargements et installations manuels, mais **ce n'est pas une mise à jour différentielle** : il retélécharge encore le paquet complet. L'automatisation est intégrée au code ; chaque nouveau format doit être validé sur une machine réelle avant d'être considéré comme stable.

## ✨ Fonctions PC

| Domaine | État vérifiable |
|:--|:--|
| Création et modification des profils, avatars et fonds locaux Windows | Code intégré ; tests automatisés réussis ; essai visuel recommandé |
| Thèmes, réglages et sauvegardes locales | Suite Desktop testée ; parcours utilisateur à valider |
| Plugins, extensions et sources configurées | Tests unitaires inclus ; dépend du fournisseur et du réseau |
| Lecteur, audio, pistes et sous-titres | Infrastructure compilée ; tests réels avec différents fichiers/flux nécessaires |
| Distribution Windows MSI + EXE + portable | **Build Windows complet réussi** |
| Autonomie Android vis-à-vis de Nuvio | **858 sources Kotlin auditées : 0 URL Nuvio non autorisée** ; la connexion aux comptes et les crédits sont conservés. [Audit et limites](./docs/AUDIT_ANDROID_NUVIO.md) |

Aucun catalogue ni contenu protégé n'est inclus avec l'application. L'utilisateur configure ses sources et respecte les droits applicables.

## ✅ Qualité et diagnostics

Le [workflow Windows](./.github/workflows/windows.yml) reconstruit les composants requis, fabrique les trois distributions, contrôle leurs formats/identités et **bloque la publication de l'artefact si la suite Desktop échoue**. Le [build Windows #25](https://github.com/anderson76389/Kovelyx/actions/runs/38507860951) a réussi les **1 554 tests exécutés, dont 1 ignoré** (aucun échec).


[**📋 Guide de tests manuels**](./docs/TESTS_PC.md) · [**📄 Guide d'installation et FAQ**](./docs/PC.md)

## 🎨 Personnalisation des profils Windows

Dans **Profils → Modifier un profil**, choisis **une photo** ou **un fond depuis ton PC**. Les images importées sont copiées dans `%APPDATA%\Kovelyx\profile-media` et redimensionnées pour limiter leur poids. Elles restent sur cet ordinateur ; les URI locaux ne sont pas synchronisés en ligne. Les fonds des catalogues réservés aux membres ne sont pas débloqués artificiellement.

## 🏗️ Architecture et organisation

```text
Kovelyx/
├── apps/
│   ├── desktop/           Application PC (Kotlin + Compose Desktop)
│   ├── mobile/            Application Android (APK universel de test)
│   └── tv/                Application Android TV / TV Box (APK expérimental)
├── vendor/engine/         Sources du moteur embarqué
├── assets/brand/          Identité graphique originale Kovelyx
├── docs/                  Site, documentation et audits
├── scripts/               Outils de contrôle et sauvegarde
└── .github/workflows/
    └── windows.yml        Installateurs Windows et tests
```

**Ne supprime pas les dossiers internes du logiciel installé** : la version Windows a besoin de son runtime, même si tu n'as téléchargé qu'un `.exe`.

## 🧭 Feuille de route — Windows, Android et TV Box

- [x] Identité Kovelyx, branding du projet et site officiel
- [x] Compilation Windows de l'installateur EXE, du MSI et du portable ZIP
- [x] Suite Desktop verte sur une révision de référence
- [x] Importation locale des photos et fonds de profil sur Windows (code)
- [ ] Tests interactifs des lecteurs, codecs, sous-titres, extensions et réglages sur Windows
- [ ] Résoudre les erreurs de chargement et tester les connexions Trakt/MDBList sur Windows
- [ ] Déployer et tester **Kovelyx Cloud** (compte unique, profils et synchronisation multi-appareils) avant d'activer l'inscription — [plan](./docs/COMPTE_UNIQUE.md) · [migration SQL](./services/kovelyx-cloud/)
- [x] APK Android universel compilé, signé et lancé sur émulateur
- [x] Premiers APK Android mobile et TV publiés sur GitHub Releases
- [ ] Finaliser la parité fonctionnelle Windows / Android ([audit](./docs/ETAT_ANDROID_ET_TV.md))
- [x] Préparer l'APK TV Box : identifiant Kovelyx, liens profonds, traductions partielles et compilation CI
- [x] Installation, lancement et navigation basique à la télécommande testés sur émulateur Google TV
- [ ] Réduire les dépendances héritées ; documenter les licences natives avant publication stable
- [ ] Releases signées et vérifiées, avec mises à jour compatibles

## 🔒 Indépendance, confidentialité et licences

Le projet vise à être **autonome vis-à-vis de l'organisation d'origine**, mais l'audit n'est pas terminé. Le code de départ est dérivé de **NuvioDesktop sous GNU GPLv3** ; les droits d'auteur, notices et obligations de fourniture des sources doivent rester accessibles. Certains binaires vidéo ont leur propre provenance et doivent être vérifiés avant toute redistribution publique.

[**🔐 Confidentialité**](https://anderson76389.github.io/Kovelyx/confidentialite.html) · [**📜 Conditions**](https://anderson76389.github.io/Kovelyx/conditions.html) · [**📖 Provenance**](./docs/PROVENANCE.md) · [**🧾 Audit des liens**](./docs/AUDIT_NUVIO.md) · [**🧩 Dépendances**](./docs/AUDIT_DEPENDANCES.md) · [**🔐 Indépendance**](./docs/INDEPENDANCE.md)

---

<div align="center">

<img src="./assets/brand/kovelyx-logo.svg" width="72" alt="Logo Kovelyx">

**KOVELYX · WINDOWS · ANDROID · ANDROID TV**

*Conçu pour ton PC, ton mobile et ta TV Box. Libre à faire évoluer.*

[Site](https://anderson76389.github.io/Kovelyx/) · [Sources](./apps/desktop/) · [Issues](https://github.com/anderson76389/Kovelyx/issues) · [Retour en haut ↑](#)

</div>
