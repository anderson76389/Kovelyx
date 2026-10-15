<div align="center">

<img src="./assets/brand/kovelyx-banner.svg" alt="Kovelyx — ton univers multimédia sur Windows et Android" width="100%" />

<br>

<img src="./assets/brand/kovelyx-wordmark.svg" alt="Kovelyx" width="300">

### Kovelyx pour Windows et Android. Ton univers multimédia.

**Deux applications disponibles en test : Windows 10/11 x64 et Android 7+ (APK universel).** Les deux sont dans **ce même dépôt**. La version Android TV est conservée dans les sources, mais n'est pas encore proposée comme téléchargement principal. Les travaux Linux sont arrêtés.

[**⬇️ Télécharger Windows et Android**](#-télécharger-kovelyx) · [**📦 GitHub Releases**](https://github.com/anderson76389/Kovelyx/releases) · [**🌐 Site officiel**](https://anderson76389.github.io/Kovelyx/) · [**📖 Guide PC**](./docs/PC.md)

[![Installer Kovelyx Windows](https://img.shields.io/badge/⬇_WINDOWS-Installer_EXE-754CE8?style=for-the-badge&logo=windows11&logoColor=white)](https://github.com/anderson76389/Kovelyx/releases/download/v0.1.247-alpha/Kovelyx-Windows-Setup-v0.1.247-alpha.exe)
[![APK Kovelyx Android](https://img.shields.io/badge/⬇_ANDROID-APK_universel-754CE8?style=for-the-badge&logo=android&logoColor=white)](https://github.com/anderson76389/Kovelyx/actions/runs/38761977979/artifacts/11986042090)
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

**Seulement deux plateformes mises en avant pour le moment : Windows et Android.** Les deux fichiers ont été réellement compilés. Ce sont des **préversions expérimentales**, pas des versions stables.

<table>
<tr>
<td width="50%" valign="top">
<h3>🪟 Windows 10 / 11 — x64</h3>
<p><strong>Installateur EXE en téléchargement direct depuis GitHub Releases.</strong></p>
<p><a href="https://github.com/anderson76389/Kovelyx/releases/download/v0.1.247-alpha/Kovelyx-Windows-Setup-v0.1.247-alpha.exe"><strong>⬇️ Télécharger Kovelyx Windows (.exe)</strong></a></p>
<p>Version vérifiée : <code>v0.1.247-alpha</code> · env. 192 Mo · <a href="https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.247-alpha">Release Windows</a>.</p>
</td>
<td width="50%" valign="top">
<h3>📱 Android 7+ — APK universel</h3>
<p><strong>Un APK de débogage unique, sans découpage en plusieurs architectures.</strong></p>
<p><a href="https://github.com/anderson76389/Kovelyx/actions/runs/38761977979/artifacts/11986042090"><strong>⬇️ Télécharger Kovelyx Android (.apk via ZIP)</strong></a></p>
<p>Build <code>4ac59948e05c</code> · env. 162 Mo pour l'APK · <a href="https://github.com/anderson76389/Kovelyx/actions/runs/38761977845">installation/démarrage sur émulateur réussis</a>.</p>
</td>
</tr>
</table>

> **Téléchargement Android :** le bouton conduit pour l'instant à un **artefact GitHub Actions** : télécharge le ZIP, décompresse-le, puis installe le fichier `.apk`. Connexion GitHub parfois nécessaire. **Expiration de cet artefact : 14 novembre 2026**. L'APK actuel n'a **pas** encore pu être ajouté à GitHub Releases (erreur GitHub **403**, droits de publication), contrairement à l'EXE Windows. Cela ne doit pas être présenté comme une publication réussie.

**📦 [Voir toutes les GitHub Releases](https://github.com/anderson76389/Kovelyx/releases) · 🌐 [Site de téléchargement](https://anderson76389.github.io/Kovelyx/) · 🔧 [Débloquer la publication des APK](./docs/RELEASES.md)**

Windows possède également des formats secondaires **MSI** et **ZIP portable**, disponibles sur la [Release Windows](https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.247-alpha). Ils ne constituent pas de nouvelles plateformes. La version TV n'est pas distribuée ici. Les anciens fichiers Linux restent dans l'historique des anciennes Releases.

**Vérifications réelles :** Windows dispose d'un build automatisé réussi ; Android a passé la compilation, la vérification de signature et d'identifiant, l'installation et le démarrage sur émulateur Android 34. **Cela ne garantit pas** toutes les fonctions multimédias, les connexions ou la compatibilité de chaque appareil. Le client Android n'est pas encore entièrement identique au client Windows.

### 🪟 Installation Windows

1. Télécharge le **Setup.exe** ci-dessus depuis GitHub Releases (pas besoin de décompresser un ZIP).
2. Vérifie l'origine GitHub et le [SHA-256 publié](https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.247-alpha) avant de lancer le programme.
3. La version portable exige d'extraire l'intégralité du ZIP ; les bibliothèques et le runtime restent nécessaires.

### 📱 Installation Android

1. Ouvre le **lien Android** ci-dessus sur GitHub et télécharge l'artefact (connexion éventuelle).
2. Décompresse l'archive pour obtenir `Kovelyx-Android-android-preview-4ac59948e05c.apk`.
3. Vérifie que ton appareil est sous Android 7.0 ou plus récent et accepte l'installation depuis cette source pour tester. **La signature Debug peut changer entre deux compilations**, ce qui peut empêcher une mise à jour sur place : sauvegarde les données avant un remplacement. L'APK Android TV et les téléviseurs ne sont pas encore certifiés.

### 🔄 Mises à jour intégrées à Kovelyx

L'application vérifie les nouvelles **Releases GitHub** au démarrage et périodiquement lorsqu'elle reste ouverte. Quand une version compatible plus récente est publiée, une **bannière en haut** permet de télécharger le fichier avec une barre de progression. Le téléchargement est contrôlé par son empreinte SHA-256 avant l'installation.

- **Windows Setup.exe / MSI :** l'application télécharge le nouvel installateur et le lance après votre clic, sans passer manuellement par le site.
- **Windows portable :** le ZIP complet est téléchargé depuis Kovelyx, vérifié puis remplacé après fermeture, avec sauvegarde de l'ancienne copie pour revenir en arrière en cas de problème.
- Les versions non compatibles ou non publiées ne sont pas proposées.

**État des APK Android :** chaque compilation mobile réussie est destinée à devenir une préversion GitHub portant son propre identifiant de commit. **GitHub renvoie encore 403 lors de la création des nouvelles Releases Android** ([diagnostic](./docs/RELEASES.md)) ; l'artefact APK existe mais une autorisation supplémentaire est nécessaire. Ce système ne garantit pas encore la mise à jour sur place des APK Debug, qui peuvent avoir des signatures différentes.

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
│   └── tv/                Sources Android TV (pas de distribution actuelle)
├── vendor/engine/         Sources du moteur embarqué
├── assets/brand/          Identité graphique originale Kovelyx
├── docs/                  Site, documentation et audits
├── scripts/               Outils de contrôle et sauvegarde
└── .github/workflows/
    └── windows.yml        Installateurs Windows et tests
```

**Ne supprime pas les dossiers internes du logiciel installé** : la version Windows a besoin de son runtime, même si tu n'as téléchargé qu'un `.exe`.

## 🧭 Feuille de route — Windows et Android

- [x] Identité Kovelyx, branding du projet et site officiel
- [x] Compilation Windows de l'installateur EXE, du MSI et du portable ZIP
- [x] Suite Desktop verte sur une révision de référence
- [x] Importation locale des photos et fonds de profil sur Windows (code)
- [ ] Tests interactifs des lecteurs, codecs, sous-titres, extensions et réglages sur Windows
- [ ] Résoudre les erreurs de chargement et tester les connexions Trakt/MDBList sur Windows
- [ ] Déployer et tester **Kovelyx Cloud** (compte unique, profils et synchronisation multi-appareils) avant d'activer l'inscription — [plan](./docs/COMPTE_UNIQUE.md) · [migration SQL](./services/kovelyx-cloud/)
- [x] APK Android universel compilé, signé et lancé sur émulateur
- [ ] Publier les nouveaux APK Android dans Releases dès que les droits GitHub le permettent
- [ ] Finaliser la parité Windows / Android avant Android TV
- [ ] Réduire les dépendances héritées ; documenter les licences natives avant publication stable
- [ ] Releases signées et vérifiées, avec mises à jour compatibles

## 🔒 Indépendance, confidentialité et licences

Le projet vise à être **autonome vis-à-vis de l'organisation d'origine**, mais l'audit n'est pas terminé. Le code de départ est dérivé de **NuvioDesktop sous GNU GPLv3** ; les droits d'auteur, notices et obligations de fourniture des sources doivent rester accessibles. Certains binaires vidéo ont leur propre provenance et doivent être vérifiés avant toute redistribution publique.

[**🔐 Confidentialité**](https://anderson76389.github.io/Kovelyx/confidentialite.html) · [**📜 Conditions**](https://anderson76389.github.io/Kovelyx/conditions.html) · [**📖 Provenance**](./docs/PROVENANCE.md) · [**🧾 Audit des liens**](./docs/AUDIT_NUVIO.md) · [**🧩 Dépendances**](./docs/AUDIT_DEPENDANCES.md) · [**🔐 Indépendance**](./docs/INDEPENDANCE.md)

---

<div align="center">

<img src="./assets/brand/kovelyx-logo.svg" width="72" alt="Logo Kovelyx">

**KOVELYX · WINDOWS ET ANDROID**

*Pensé pour ton PC. Libre à faire évoluer.*

[Site](https://anderson76389.github.io/Kovelyx/) · [Sources](./apps/desktop/) · [Issues](https://github.com/anderson76389/Kovelyx/issues) · [Retour en haut ↑](#)

</div>
