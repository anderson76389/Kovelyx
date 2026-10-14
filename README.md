<div align="center">

<img src="assets/brand/kovelyx-banner.svg" alt="KOVELYX — Your media. Your world." width="100%" />

<img src="assets/brand/kovelyx-wordmark.svg" alt="Signature Kovelyx" width="350" />

<br />

**Un seul univers multimédia. Trois écrans. Le contrôle entre tes mains.**

<br />

[![Plateformes](https://img.shields.io/badge/PLATEFORMES-Windows%20%7C%20Android%20%7C%20Android%20TV-8761EA?style=for-the-badge&labelColor=111125)](https://github.com/anderson76389/Kovelyx)
[![Licence](https://img.shields.io/badge/LICENCE-GPLv3-A47BFF?style=for-the-badge&labelColor=111125)](#-licences-et-crédits)
[![Développement](https://img.shields.io/badge/STATUT-EN%20DÉVELOPPEMENT-6A689E?style=for-the-badge&labelColor=111125)](#-état-du-projet)

<br />

[**Téléchargements**](./docs/index.html) · [**Releases**](https://github.com/anderson76389/Kovelyx/releases) · [**Plateformes**](#-sur-vos-trois-écrans) · [**Installation**](#-installation-et-compilation) · [**Avancement**](#-état-du-projet)

</div>

---

## ✦ Découvrir Kovelyx

**Kovelyx** est un projet libre d'application multimédia pour **Windows, Android et Android TV**, construit à partir de bases open source existantes.

L'objectif : conserver une expérience de découverte et de lecture agréable, personnalisable et compatible avec les extensions et sources prises en charge, mais avec **une application que tu peux compiler, sauvegarder et maintenir toi-même**.

<table>
<tr>
<td width="33%" align="center"><strong>✦ Une interface soignée</strong><br /><sub>Films, séries, bibliothèques et recherche : une expérience cohérente selon l'écran.</sub></td>
<td width="33%" align="center"><strong>⌁ Des sources au choix</strong><br /><sub>Compatibilité avec les addons et protocoles déjà utilisés par les bases du projet, sous réserve de tests.</sub></td>
<td width="33%" align="center"><strong>◇ Tes réglages sur ton appareil</strong><br /><sub>Pas de serveur Kovelyx à installer : configuration et données conservées localement, objectif en cours d'intégration.</sub></td>
</tr>
</table>

## ⬇️ Téléchargements

<div align="center">

**[🌐 Site de téléchargement Kovelyx](https://anderson76389.github.io/Kovelyx/)** · **[📦 Releases GitHub](https://github.com/anderson76389/Kovelyx/releases)** · **[⚙️ Configuration et téléchargement](./docs/TELECHARGEMENTS.md)**

</div>

| Appareil | Fichier prévu | Disponibilité |
| :--- | :--- | :--- |
| 📺 Android TV / Google TV | `Kovelyx-TV-preview-…apk` | Préversions expérimentales après compilation réussie |
| 📱 Android mobile | `Kovelyx-Mobile-preview-…apk` | Préversions expérimentales après compilation réussie |
| 🖥️ Windows | `Kovelyx-Windows-…msi` | Pas de Release validée |

Le [site de téléchargement](https://anderson76389.github.io/Kovelyx/) vérifie les **vrais fichiers des Releases** et active les boutons automatiquement, sans inventer de téléchargements. Le site est publié à partir de la branche **gh-pages** et mis à jour automatiquement depuis `docs/`. Si nécessaire, sélectionner **Settings → Pages → Deploy from a branch → gh-pages / (root)**. Les APK de préversion n'apparaissent qu'après une compilation réussie. Ils ne sont pas encore validés sur appareils réels. Le MSI Windows reste soumis à ses propres vérifications.

## 📺 Sur vos trois écrans

<table>
<thead><tr><th align="center">Android TV / Google TV</th><th align="center">Android mobile</th><th align="center">Windows</th></tr></thead>
<tbody>
<tr>
<td align="center"><h3>▣ TV</h3><sub>Navigation à la télécommande<br/>Interface grand écran</sub></td>
<td align="center"><h3>▯ MOBILE</h3><sub>Navigation tactile<br/>Téléphone et tablette Android</sub></td>
<td align="center"><h3>▤ DESKTOP</h3><sub>Application de bureau<br/>Souris et clavier</sub></td>
</tr>
<tr>
<td align="center"><a href="./apps/tv"><strong>Explorer le code TV →</strong></a></td>
<td align="center"><a href="./apps/mobile"><strong>Explorer le code mobile →</strong></a></td>
<td align="center"><a href="./apps/desktop"><strong>Explorer le code PC →</strong></a></td>
</tr>
</tbody>
</table>

> **À savoir :** les trois dossiers contiennent les **sources importées**, pas encore trois applications Kovelyx finalisées ou certifiées. La branche Desktop de référence est toujours en développement alpha.

## 💜 Sans serveur personnel

Pas besoin de louer un VPS, d'installer Docker ou de créer un compte sur un serveur Kovelyx.

| Sur tes appareils | Sur Internet, seulement lorsque nécessaire |
| :--- | :--- |
| Préférences et configuration locales **à rendre autonomes et tester** | Récupération des catalogues, métadonnées et visuels depuis leurs fournisseurs |
| Sources et extensions configurées sur chaque installation | Connexion aux serveurs utilisés par certains addons |
| Bibliothèque, favoris et historique **à vérifier selon les capacités de chaque client** | Accès aux flux et services externes choisis |
| Sauvegarde/export manuel : **fonction à étudier** | Mises à jour applicatives depuis les Releases Kovelyx, lorsque disponibles |

**Ce que « sans serveur » signifie :** pas de *backend personnel Kovelyx*. Cela **ne signifie pas** que tous les contenus seront disponibles hors ligne, que les addons fonctionneront sans Internet ou que les appareils se synchroniseront automatiquement. Sans serveur partagé, les données de chaque installation restent séparées, sauf mécanisme explicite d'import/export.

## ⚡ Expérience visée

| Domaine | Objectif |
| --- | --- |
| 🎞️ **Découverte** | Films, séries et informations riches suivant les sources compatibles |
| 🧩 **Extensions** | Installer et gérer les addons compatibles avec les clients |
| ▶️ **Lecture** | Lecteurs vidéo, audio et sous-titres selon les capacités réelles de chaque version |
| 🎨 **Personnalisation** | Interface et thèmes adaptés à la TV, au mobile et au PC |
| ♡ **Bibliothèque** | Favoris, progression et données conservés localement lorsque les fonctions seront validées |
| ⬇️ **Distribution** | APK pour Android et Android TV, installateur Windows, publiés par Kovelyx |
| 🛡️ **Indépendance** | Sources archivées, références à Nuvio auditées, builds reproductibles |

Ces points constituent **la feuille de route**, et non une affirmation que toutes les fonctions marchent déjà.

## 🚧 État du projet

| Composant | Sources | Compilation Kovelyx |
| --- | :---: | :---: |
| Android TV | ✅ Importées | 🧪 Non validée |
| Android mobile | ✅ Importées | 🧪 Non validée |
| Windows | ✅ Importées | 🧪 Non validée |
| Configuration sans serveur | 🎯 Objectif | ⏳ À adapter et tester |
| Marque Kovelyx | ✅ Logo et bannière du README | ⏳ Intégration dans les applications |

Les premières tentatives de compilation n'ont pas encore produit de paquet Kovelyx validé. Des dépendances natives et des configurations de build restent à corriger ; la version Windows nécessite notamment la restauration d'un binaire vidéo absent du dépôt Git.

**[Voir les tests de compilation →](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)** · **[Voir les problèmes ouverts →](https://github.com/anderson76389/Kovelyx/issues)**

> Il n'y a **pas encore de téléchargement officiel APK ou MSI Kovelyx**. Aucun badge « build réussi » ou lien vers une Release inexistante n'est affiché.

## 🛠️ Installation et compilation

Il s'agit pour l'instant de compilations **de développement à partir des sources**, et non d'une procédure d'installation grand public.

<details>
<summary><strong>📺 Android TV — compilation de référence</strong></summary>

Nécessite un JDK et le SDK Android configurés. Depuis PowerShell sous Windows :

```powershell
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx\apps\tv
.\gradlew.bat :app:assembleFullDebug
```

L'APK ne doit être considéré comme utilisable qu'après résolution des dépendances et tests d'installation.

</details>

<details>
<summary><strong>📱 Android mobile — compilation de référence</strong></summary>

```powershell
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx\apps\mobile
.\gradlew.bat :androidApp:assembleFullDebug
```

Nécessite aussi le SDK Android et une configuration Gradle valide.

</details>

<details>
<summary><strong>🖥️ Windows — compilation de référence</strong></summary>

```powershell
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx\apps\desktop
.\gradlew.bat :composeApp:packageReleaseMsi
```

⚠️ La compilation et la lecture vidéo sur Windows sont **bloquées** tant que les dépendances natives manquantes ne sont pas restaurées et vérifiées. Voir [l'inventaire des binaires](./apps/desktop/.kovelyx-binaries-a-sauvegarder.txt).

</details>

## 🗂️ Organisation du dépôt

```text
Kovelyx/
├── assets/
│   └── brand/                # Logo et bannière du projet
├── apps/
│   ├── tv/                   # Android TV / Google TV
│   ├── mobile/               # Android mobile
│   └── desktop/              # Windows
├── docs/
│   ├── INDEPENDANCE.md       # Critères techniques d'autonomie
│   └── PROVENANCE.md         # Sources et commits de référence
├── scripts/                  # Import des sources et outils d'audit
└── .github/workflows/        # Automatisations et essais de compilation
```

**Aucun dossier backend/serveur n'est requis pour l'architecture cible.** Les trois projets conservent leur structure Gradle, leur code et les notices juridiques d'origine.

## 🧭 Feuille de route

- [x] Réunir les trois codes sources dans un dépôt indépendant.
- [x] Créer le logo et la bannière Kovelyx pour la documentation.
- [x] Retirer le serveur personnel de l'architecture du projet.
- [ ] Résoudre les dépendances natives et obtenir trois compilations réussies.
- [ ] Attribuer des identifiants, icônes, noms et écrans de marque propres à Kovelyx.
- [ ] Retirer les références fonctionnelles aux mises à jour et comptes Nuvio.
- [ ] Valider le stockage local des réglages, addons et données personnelles.
- [ ] Tester la lecture, la navigation, les sous-titres, les thèmes et les extensions sur des appareils réels.
- [ ] Publier des Releases Kovelyx reproductibles (APK TV, APK mobile et MSI Windows).

Pour l'état réellement vérifié et les limites d'autonomie, consultez **[l'état technique](./docs/ETAT_TECHNIQUE.md)**, **[l'inventaire des dépendances](./docs/AUDIT_DEPENDANCES.md)** et **[le plan d'indépendance](./docs/INDEPENDANCE.md)**.

Pour conserver une copie **hors GitHub** des sources et des Releases accessibles, lancez sur Windows le script [Sauvegarder-Kovelyx.ps1](./scripts/Sauvegarder-Kovelyx.ps1). Une sauvegarde des fichiers de source ne remplace pas l'archivage des dépendances natives, des SDK et de la clé de signature Android.

## ⚖️ Licences et crédits

Kovelyx est un projet **indépendant**, non affilié à NuvioMedia. Ses sources initiales proviennent des projets publics [NuvioTV](https://github.com/NuvioMedia/NuvioTV), [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) et [NuvioDesktop](https://github.com/NuvioMedia/NuvioDesktop).

Ces applications sont distribuées sous **GNU GPL v3** ; le code dérivé reste soumis aux obligations applicables de cette licence, notamment la conservation des notices et l'accès au code source correspondant. Les composants tiers (dont MPVKit et les bibliothèques natives) ont leurs propres exigences ; leur provenance doit être vérifiée avant la redistribution de binaires.

Les marques et éléments graphiques présents dans le code amont ne deviennent pas des éléments officiels Kovelyx. Le nouveau logo SVG du README est distinct.

📖 **[Détails des sources et des licences →](./docs/PROVENANCE.md)**

---

<div align="center">

<img src="assets/brand/kovelyx-logo.svg" width="68" alt="Icône Kovelyx" />

### KOVELYX

*Your media. Your world.*

**Fait pour durer. Développé librement. Conçu pour tes écrans.**

[Revenir en haut ↑](#)

</div>
