<div align="center">

<img src="./assets/brand/kovelyx-banner.svg" alt="Kovelyx — ton univers multimédia sur ordinateur" width="100%" />

<br>

<img src="./assets/brand/kovelyx-wordmark.svg" alt="Kovelyx" width="300">

### Une seule application. Deux systèmes. Ton espace multimédia.

**Kovelyx Desktop** est un projet multimédia open source destiné aux **PC Windows et Linux**.

[**🌐 Site officiel**](https://anderson76389.github.io/Kovelyx/) · [**⬇️ Téléchargements**](#-télécharger-kovelyx) · [**🛠️ Sources PC**](./apps/desktop/) · [**📖 Guide PC**](./docs/PC.md)

[![Windows](https://img.shields.io/badge/Windows-10%20%2F%2011-754CE8?style=for-the-badge&logo=windows11&logoColor=white)](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
[![Linux](https://img.shields.io/badge/Linux-x86--64-754CE8?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml)
[![GPL--3.0](https://img.shields.io/badge/Licence-GPLv3-49435C?style=for-the-badge)](./docs/PROVENANCE.md)

[![Compilation Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
[![Compilation Linux](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml)

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

| Système | Format principal | Ce que tu récupères | État |
|:--|:--|:--|:--|
| **Windows x64** | `Kovelyx-Windows-Setup-….exe` | **1 fichier** pour installer l'application et son environnement d'exécution | [Build automatisé réussi](https://github.com/anderson76389/Kovelyx/actions/runs/38507860951) |
| **Linux x86-64** | `Kovelyx-Linux-x86_64-….AppImage` | **1 fichier exécutable** à lancer sans décompresser un gros dossier | [Compilation en cours de validation](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml) |
| Windows x64 (alternative) | `.msi` | Installateur Windows classique | [Disponible dans les artefacts des builds réussis](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) |
| Windows x64 (alternative) | `Portable.zip` | Dossier portable avec lanceur et runtime | [Disponible dans les artefacts des builds réussis](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) |

**Téléchargements :** [GitHub Actions](https://github.com/anderson76389/Kovelyx/actions) · [Releases officielles](https://github.com/anderson76389/Kovelyx/releases) · [Site Kovelyx](https://anderson76389.github.io/Kovelyx/)

> **Important :** sous Windows, le `Setup.exe` est un fichier unique **pour l'installation**. Le logiciel une fois installé contient normalement de nombreux fichiers internes (bibliothèques, Java, codecs). Linux ne lance pas les `.exe` Windows de façon native : le fichier unique adapté est une **AppImage**. Une AppImage peut encore nécessiter certaines bibliothèques présentes sur la distribution. **Un build réussi ne garantit pas toutes les fonctions en situation réelle.**

### 🪟 Windows : le plus simple

1. Ouvre [**le dernier workflow Windows réussi**](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) et vérifie son statut **Success**.
2. Dans **Artifacts**, télécharge `Kovelyx-Windows-installers-experimental` (connexion GitHub souvent nécessaire).
3. Décompresse l'archive téléchargée **une seule fois**, puis lance `Kovelyx-Windows-Setup-….exe`. Le MSI et le ZIP portable sont fournis en alternative.
4. Si Windows SmartScreen demande une confirmation, vérifie le dépôt, l'origine du fichier et son SHA-256. Ne contourne pas les avertissements pour un binaire inconnu.

### 🐧 Linux : une AppImage

Le workflow Linux prépare une **AppImage x86-64 expérimentale**. Dès qu'une compilation est verte, récupère le fichier dans ses artefacts :

```bash
chmod +x Kovelyx-Linux-x86_64-*.AppImage
./Kovelyx-Linux-x86_64-*.AppImage
```

Pas de `sudo` pour lancer l'AppImage. Les bibliothèques vidéo, WebKit/GTK et la compatibilité graphique restent à tester suivant la distribution (voir le [guide Linux](./docs/LINUX.md)). L'AppImage **ne fonctionne pas automatiquement sur ARM64**.

## ✨ Fonctions PC

| Domaine | État vérifiable |
|:--|:--|
| Création et modification des profils, avatars et fonds locaux Windows | Code intégré ; tests automatisés réussis ; essai visuel recommandé |
| Thèmes, réglages et sauvegardes locales | Suite Desktop testée ; parcours utilisateur à valider |
| Plugins, extensions et sources configurées | Tests unitaires inclus ; dépend du fournisseur et du réseau |
| Lecteur, audio, pistes et sous-titres | Infrastructure compilée ; tests réels avec différents fichiers/flux nécessaires |
| Distribution Windows MSI + EXE + portable | **Build Windows complet réussi** |
| Distribution Linux AppImage | **Préparation du pipeline ; compatibilité Linux non encore certifiée** |
| Autonomie vis-à-vis de Nuvio | **Audit en cours**, certains composants techniques/dépendances demeurent |

Aucun catalogue ni contenu protégé n'est inclus avec l'application. L'utilisateur configure ses sources et respecte les droits applicables.

## ✅ Qualité et diagnostics

Le [workflow Windows](./.github/workflows/windows.yml) reconstruit les composants requis, fabrique les trois distributions, contrôle leurs formats/identités et **bloque la publication de l'artefact si la suite Desktop échoue**. Le [build Windows #25](https://github.com/anderson76389/Kovelyx/actions/runs/38507860951) a réussi les **1 554 tests exécutés, dont 1 ignoré** (aucun échec).

Le [workflow Linux](./.github/workflows/linux.yml) tente de produire une AppImage en un fichier et vérifie l'exécutable, le lanceur interne, le logo et le SHA-256 avant l'enregistrement. Une validation sur Ubuntu, Fedora et différentes configurations reste indispensable.

[**📋 Guide de tests manuels**](./docs/TESTS_PC.md) · [**📄 Guide d'installation et FAQ**](./docs/PC.md) · [**🐧 Linux**](./docs/LINUX.md)

## 🎨 Personnalisation des profils Windows

Dans **Profils → Modifier un profil**, choisis **une photo** ou **un fond depuis ton PC**. Les images importées sont copiées dans `%APPDATA%\Kovelyx\profile-media` et redimensionnées pour limiter leur poids. Elles restent sur cet ordinateur ; les URI locaux ne sont pas synchronisés en ligne. Les fonds des catalogues réservés aux membres ne sont pas débloqués artificiellement.

## 🏗️ Architecture et organisation

```text
Kovelyx/
├── apps/
│   ├── desktop/           Application PC (Kotlin + Compose Desktop)
│   ├── mobile/            Sources historiques conservées, hors priorité
│   └── tv/                Sources historiques conservées, hors priorité
├── vendor/engine/         Sources du moteur embarqué
├── assets/brand/          Identité graphique originale Kovelyx
├── docs/                  Site, documentation et audits
├── scripts/               Outils de contrôle et sauvegarde
└── .github/workflows/
    ├── windows.yml        Installateurs Windows et tests
    └── linux.yml          AppImage Linux expérimentale
```

**Ne supprime pas les dossiers internes du logiciel installé** : la version Windows a besoin de son runtime, même si tu n'as téléchargé qu'un `.exe`.

## 🧭 Feuille de route PC

- [x] Identité Kovelyx, branding du projet et site officiel
- [x] Compilation Windows de l'installateur EXE, du MSI et du portable ZIP
- [x] Suite Desktop verte sur une révision de référence
- [x] Importation locale des photos et fonds de profil sur Windows (code)
- [ ] AppImage Linux construite et testée sur différentes distributions
- [ ] Tests interactifs des lecteurs, codecs, sous-titres, extensions et réglages sur les deux OS
- [ ] Réduire les dépendances héritées ; documenter les licences natives avant publication stable
- [ ] Releases signées et vérifiées, avec mises à jour compatibles

## 🔒 Indépendance, confidentialité et licences

Le projet vise à être **autonome vis-à-vis de l'organisation d'origine**, mais l'audit n'est pas terminé. Le code de départ est dérivé de **NuvioDesktop sous GNU GPLv3** ; les droits d'auteur, notices et obligations de fourniture des sources doivent rester accessibles. Certains binaires vidéo ont leur propre provenance et doivent être vérifiés avant toute redistribution publique.

[**📖 Provenance**](./docs/PROVENANCE.md) · [**🧾 Audit des liens**](./docs/AUDIT_NUVIO.md) · [**🧩 Dépendances**](./docs/AUDIT_DEPENDANCES.md) · [**🔐 Indépendance**](./docs/INDEPENDANCE.md)

---

<div align="center">

<img src="./assets/brand/kovelyx-logo.svg" width="72" alt="Logo Kovelyx">

**KOVELYX · DESKTOP**

*Pensé pour ton PC. Libre à faire évoluer.*

[Site](https://anderson76389.github.io/Kovelyx/) · [Sources](./apps/desktop/) · [Issues](https://github.com/anderson76389/Kovelyx/issues) · [Retour en haut ↑](#)

</div>
