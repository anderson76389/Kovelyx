<div align="center">

<img src="./assets/brand/kovelyx-banner.svg" alt="Kovelyx — ton univers multimédia sur ordinateur" width="100%" />

<br>

<img src="./assets/brand/kovelyx-wordmark.svg" alt="Kovelyx" width="300">

### Une seule application. Deux systèmes. Ton espace multimédia.

**Kovelyx Desktop** est un projet multimédia open source développé en priorité pour **Windows 10/11**. Android puis TV viendront après stabilisation de la version Windows.

[**🌐 Site officiel**](https://anderson76389.github.io/Kovelyx/) · [**⬇️ Téléchargements**](#-télécharger-kovelyx) · [**🛠️ Sources PC**](./apps/desktop/) · [**📖 Guide PC**](./docs/PC.md)

[![Windows](https://img.shields.io/badge/Windows-10%20%2F%2011-754CE8?style=for-the-badge&logo=windows11&logoColor=white)](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
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

| Système | Format principal | Ce que tu récupères | État |
|:--|:--|:--|:--|
| **Windows x64** | `Kovelyx-Windows-Setup-….exe` | **1 fichier** pour installer l'application et son environnement d'exécution | [Build automatisé réussi](https://github.com/anderson76389/Kovelyx/actions/runs/38507860951) |
| Windows x64 (alternative) | `.msi` | Installateur Windows classique | [Disponible dans les artefacts des builds réussis](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) |
| Windows x64 (alternative) | `Portable.zip` | Dossier portable avec lanceur et runtime | [Disponible dans les artefacts des builds réussis](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) |


**Préversions Windows :** dès qu'une compilation et ses tests automatisés réussissent, le `Setup.exe` est publié directement dans les [Releases GitHub](https://github.com/anderson76389/Kovelyx/releases) sans archive ZIP de GitHub Actions. Le MSI est une alternative ; la version portable conserve ses dépendances dans un ZIP. Ce sont des versions de test non signées, pas des versions stables. Linux nécessite encore ses propres vérifications.

**Tous les téléchargements :** [GitHub Actions](https://github.com/anderson76389/Kovelyx/actions) · [Releases officielles](https://github.com/anderson76389/Kovelyx/releases) · [Site Kovelyx](https://anderson76389.github.io/Kovelyx/)

> **Important :** sous Windows, le `Setup.exe` est un fichier unique **pour l'installation**. Le logiciel une fois installé contient toujours ses bibliothèques, Java et codecs internes. **Un build réussi ne garantit pas toutes les fonctions en situation réelle.**

### 🪟 Windows : le plus simple

1. Ouvre [**le dernier workflow Windows réussi**](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) et vérifie son statut **Success**.
2. Dans **Artifacts**, télécharge `Kovelyx-Windows-installers-experimental` (connexion GitHub souvent nécessaire).
3. Décompresse l'archive téléchargée **une seule fois**, puis lance `Kovelyx-Windows-Setup-….exe`. Le MSI et le ZIP portable sont fournis en alternative.
4. Si Windows SmartScreen demande une confirmation, vérifie le dépôt, l'origine du fichier et son SHA-256. Ne contourne pas les avertissements pour un binaire inconnu.

### 🔄 Mises à jour intégrées à Kovelyx

L'application vérifie les nouvelles **Releases GitHub** au démarrage et périodiquement lorsqu'elle reste ouverte. Quand une version compatible plus récente est publiée, une **bannière en haut** permet de télécharger le fichier avec une barre de progression. Le téléchargement est contrôlé par son empreinte SHA-256 avant l'installation.

- **Windows Setup.exe / MSI :** l'application télécharge le nouvel installateur et le lance après votre clic, sans passer manuellement par le site.
- **Windows portable :** le ZIP complet est téléchargé depuis Kovelyx, vérifié puis remplacé après fermeture, avec sauvegarde de l'ancienne copie pour revenir en arrière en cas de problème.
- Les versions non compatibles ou non publiées ne sont **pas** proposées. Flatpak n'a pas d'auto-installation depuis Kovelyx.

**Important :** ce mécanisme évite les téléchargements et installations manuels, mais **ce n'est pas une mise à jour différentielle** : il retélécharge encore le paquet complet. L'automatisation est intégrée au code ; chaque nouveau format doit être validé sur une machine réelle avant d'être considéré comme stable.

## ✨ Fonctions PC

| Domaine | État vérifiable |
|:--|:--|
| Création et modification des profils, avatars et fonds locaux Windows | Code intégré ; tests automatisés réussis ; essai visuel recommandé |
| Thèmes, réglages et sauvegardes locales | Suite Desktop testée ; parcours utilisateur à valider |
| Plugins, extensions et sources configurées | Tests unitaires inclus ; dépend du fournisseur et du réseau |
| Lecteur, audio, pistes et sous-titres | Infrastructure compilée ; tests réels avec différents fichiers/flux nécessaires |
| Distribution Windows MSI + EXE + portable | **Build Windows complet réussi** |
| Indépendance de Kovelyx | Dépôt, fichiers d'installation et marque Kovelyx autonomes ; services tiers optionnels et obligations de provenance conservés |

Aucun catalogue ni contenu protégé n'est inclus avec l'application. L'utilisateur configure ses sources et respecte les droits applicables.

## ✅ Qualité et diagnostics

Le [workflow Windows](./.github/workflows/windows.yml) reconstruit les composants requis, fabrique les trois distributions, contrôle leurs formats/identités et **bloque la publication de l'artefact si la suite Desktop échoue**. Le [build Windows #25](https://github.com/anderson76389/Kovelyx/actions/runs/38507860951) a réussi les **1 554 tests exécutés, dont 1 ignoré** (aucun échec).

Les workflows Linux ne sont plus déclenchés automatiquement : **Windows uniquement pour les prochaines Releases**, puis développement Android et TV.

[**📋 Guide de tests manuels**](./docs/TESTS_PC.md) · [**📄 Guide d'installation et FAQ**](./docs/PC.md)

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
    └── linux.yml          Compilations Linux en pause (manuel uniquement)
```

**Ne supprime pas les dossiers internes du logiciel installé** : la version Windows a besoin de son runtime, même si tu n'as téléchargé qu'un `.exe`.

## 🧭 Feuille de route PC

- [x] Identité Kovelyx, branding du projet et site officiel
- [x] Compilation Windows de l'installateur EXE, du MSI et du portable ZIP
- [x] Suite Desktop verte sur une révision de référence
- [x] Importation locale des photos et fonds de profil sur Windows (code)
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
