<div align="center">

[**Télécharger la dernière version — Windows · Linux · Android · Android TV**](https://github.com/anderson76389/Kovelyx/releases/latest)

Les profils TV reçoivent les noms, photos et fonds du compte ; leur modification se fait sur Android mobile ou PC.

<img src="./assets/brand/kovelyx-banner.svg" alt="Kovelyx : votre univers multimédia sur Windows, Android et Android TV" width="100%">

<br>

### Votre univers multimédia. Vos écrans. Vos choix.

**Kovelyx** réunit découverte, bibliothèque, lecture et personnalisation dans quatre déclinaisons conçues pour **Windows, Linux, Android et Android TV**.

[![Site officiel](https://img.shields.io/badge/SITE_OFFICIEL-DÉCOUVRIR-8656F4?style=for-the-badge&logo=googlechrome&logoColor=white)](https://anderson76389.github.io/Kovelyx/)
[![Téléchargements](https://img.shields.io/badge/RELEASES-TÉLÉCHARGER-B784FF?style=for-the-badge&logo=github&logoColor=white)](https://github.com/anderson76389/Kovelyx/releases)

[![Licence](https://img.shields.io/badge/LICENCE-GPLv3-33304A?style=for-the-badge)](./docs/PROVENANCE.md)

<sub>Projet open source • Versions actuellement en préversion • Sources et builds hébergés dans ce dépôt</sub>

</div>

---

## ✦ Quatre plateformes. Un seul univers.

<div align="center">

<table align="center" width="100%">
  <thead>
    <tr>
      <th align="center" colspan="3">
        <img src="./assets/brand/kovelyx-platforms.svg" alt="Illustration centrée des applications Windows, Android et Android TV" width="100%">
        <p><sub>Illustration conceptuelle, non issue d'une application installée.</sub></p>
      </th>
    </tr>
    <tr>
      <th align="center" width="33%">🖥️ Windows</th>
      <th align="center" width="34%">📱 Android mobile</th>
      <th align="center" width="33%">📺 Android TV</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">Windows 10 et 11 · x64</td>
      <td align="center">Téléphones et tablettes · Android 7+</td>
      <td align="center">Télévisions et TV Box · Android 7+</td>
    </tr>
    <tr>
      <td align="center">EXE · MSI · ZIP portable</td>
      <td align="center">APK Android universel</td>
      <td align="center">APK TV universel · télécommande</td>
    </tr>
    <tr>
      <td align="center"><a href="https://anderson76389.github.io/Kovelyx/?plateforme=desktop#plateformes"><strong>Télécharger Windows</strong></a></td>
      <td align="center"><a href="https://anderson76389.github.io/Kovelyx/?plateforme=android#plateformes"><strong>Télécharger Android</strong></a></td>
      <td align="center"><a href="https://anderson76389.github.io/Kovelyx/?plateforme=tv#plateformes"><strong>Télécharger Android TV</strong></a></td>
    </tr>
  </tbody>
</table>

</div>

<div align="center">

### 🐧 Linux — même application Desktop que Windows

La version Linux utilise le **même code Compose Desktop et le même moteur multimédia** que Windows. Les nouvelles Releases vérifiées proposent **AppImage x86‑64** (distribution portable), **DEB amd64** (Debian/Ubuntu compatibles) et **RPM x86‑64** (Fedora et distributions compatibles). Pour chaque publication, les trois paquets Linux et leurs empreintes SHA‑256 proviennent **du même commit** que Windows, Android et TV ; l’absence d’un paquet Linux validé empêche la publication complète.

**[Télécharger Kovelyx Linux dans les Releases](https://github.com/anderson76389/Kovelyx/releases/latest)** · [Guide Linux et limites](./apps/desktop/README.md#linux)

*Compatibilité vérifiée sur Linux x86‑64 seulement : ARM64, systèmes 32 bits et autres architectures ne sont pas encore certifiés. Un lancement automatisé sous Xvfb ne remplace pas un essai sur votre distribution.*

**[🌐 Accéder au site des applications](https://anderson76389.github.io/Kovelyx/) · [📦 Voir toutes les Releases](https://github.com/anderson76389/Kovelyx/releases)**

</div>

> **Important :** les fichiers proposés sont des **préversions expérimentales**, pas des versions stables certifiées. Pour les APK de test, la signature peut changer : une mise à jour par-dessus la précédente peut échouer. Sauvegardez vos données locales avant de remplacer ou désinstaller une application.

---

## ✨ Une expérience qui vous ressemble

<table>
<tr>
<td width="50%" valign="top"><h3>🎬 Explorer et retrouver</h3><p>Parcourir des catalogues, chercher des films et séries et organiser sa bibliothèque autour de ses sources et extensions compatibles.</p></td>
<td width="50%" valign="top"><h3>▶️ Lire à sa façon</h3><p>Lecteur intégré, sélection de sources, gestion des pistes audio et sous-titres, reprise de lecture et réglages selon la plateforme.</p></td>
</tr>
<tr>
<td valign="top"><h3>🎨 Une interface personnelle</h3><p>Thèmes, profils, avatars et arrière-plans personnalisés. L'importation de photos, GIF et vidéos fait l'objet d'une nouvelle version en cours de validation.</p></td>
<td valign="top"><h3>🧩 Des connexions modulaires</h3><p>Extensions, catalogues, Trakt, MDBList et services tiers configurables selon les fonctionnalités disponibles et les identifiants requis.</p></td>
</tr>
</table>

**Aucun catalogue de contenu protégé ni flux vidéo n'est fourni avec Kovelyx.** Les disponibilités dépendent des sources et fournisseurs configurés par l'utilisateur, ainsi que des droits applicables.

## ☁️ Votre compte Kovelyx — état vérifié au 8 novembre 2026

L'infrastructure de comptes Kovelyx repose sur **Cloudflare Workers, D1 et Better Auth**, sans connexion nécessaire aux anciens fournisseurs de comptes.

| Vérification | État vérifié |
|:---|:---|
| Comptes, profils et PIN Cloudflare | Migration D1 `0005_profile_pins.sql` appliquée en production après sauvegarde chiffrée et restauration contrôlée ; [workflow réussi](https://github.com/anderson76389/Kovelyx/actions/runs/42438155964). |
| Médias de profil et catalogues | Endpoint de production `/ready` contrôlé après migration : D1 `0004`, `profilePinSchema: 1`, `catalogueSnapshotSchema: 1`. |
| Ma Collection → Kovelyx | Publication du JSON français et fusion de dossiers testées sur **Worker + D1 locaux jetables** avec trois sessions indépendantes simulant Windows, Android et TV ([PR nº 95](https://github.com/anderson76389/Kovelyx/pull/95)). |
| Bibliothèque, œuvres vues et progression | Tests de synchronisation intersessions sur Cloudflare/D1 local. |
| Partage entre profils | Copie **manuelle** et sélective des extensions et catalogues vers d'autres profils du **même compte**, sans recopier PIN, historique, bibliothèque ou progression ([PR nº 96](https://github.com/anderson76389/Kovelyx/pull/96)). |
| Applications Windows, Android et TV | Packages communs publiés en [préversion v1.1.1068](https://github.com/anderson76389/Kovelyx/releases/tag/v1.1.1068) ; tests automatisés et émulateurs réalisés. |

**Limites importantes :** ces validations ne certifient pas la synchronisation sur trois appareils physiques, tous les réseaux et tous les comptes existants. La présence d'un APK récent ne met pas à jour automatiquement le Worker. Kaptain Stream utilise nativement l'intégration Nuvio : pour Kovelyx, utiliser l'export/import compatible Ma Collection plutôt que supposer qu'un push Kaptain arrive directement sur le compte Kovelyx.

**[Documentation Cloudflare](./services/kovelyx-cloudflare/README.md) · [Contrôle de disponibilité en lecture seule](./.github/workflows/cloudflare-readiness.yml) · [Tests GitHub Actions](https://github.com/anderson76389/Kovelyx/actions)**

---

> **Autonomie du service de comptes :** un [mode de reprise hors Cloudflare avec Node.js et SQLite](./docs/PORTABILITE_BACKEND.md) est disponible pour les reconstructions privées. Son fonctionnement sur de vrais comptes et la bascule des trois applications nécessitent encore une validation de bout en bout.

## 🛠️ Code, tests et état technique

Le projet rassemble les sources de ses trois applications, l'infrastructure de compte et les outils de compilation dans **un seul dépôt**.

| Répertoire | Contenu |
|:---|:---|
| [apps/desktop](./apps/desktop/) | Applications Windows et Linux / Compose Desktop |
| [apps/mobile](./apps/mobile/) | Application Android mobile |
| [apps/tv](./apps/tv/) | Application Android TV et TV Box |
| [services/kovelyx-cloudflare](./services/kovelyx-cloudflare/) | Worker de comptes, base D1 et synchronisation |
| [vendor/engine](./vendor/engine/) | Sources du moteur conservées dans le dépôt |
| [assets/brand](./assets/brand/) | Logo, bannière et visuels de présentation |
| [docs](./docs/) | Site, guides, audits et documentation |

**Contrôles automatiques**

[![Build Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
[![Builds APK](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)
[![Cloudflare](https://github.com/anderson76389/Kovelyx/actions/workflows/cloudflare-check.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/cloudflare-check.yml)
[![Tests fonctionnels](https://github.com/anderson76389/Kovelyx/actions/workflows/functional-verification.yml/badge.svg)](https://github.com/anderson76389/Kovelyx/actions/workflows/functional-verification.yml)

Le code peut être compilé avec les anciens domaines Nuvio bloqués dans les scénarios testés. En revanche, **une compilation réussie ne prouve pas que chaque bouton, addon, lecteur ou connexion externe fonctionne en situation réelle**. Les tests Kotlin Android/TV et plusieurs scénarios d'émulateur récents ont réussi ; ils ne remplacent pas la validation du lecteur, des sources et de la télécommande sur appareils physiques.

**[Audit d'indépendance](./docs/CONTROLES_INDEPENDANCE.md) · [État technique](./docs/ETAT_TECHNIQUE.md) · [Tests manuels PC](./docs/TESTS_PC.md) · [Suivi GitHub Actions](https://github.com/anderson76389/Kovelyx/actions)**

## 🧭 Feuille de route

- [x] Trois projets Windows / Android / TV regroupés dans le même dépôt.
- [x] Identité Kovelyx, site de téléchargement et premières Releases par plateforme.
- [x] Compilations et premiers tests de lancement avec anciens domaines Nuvio bloqués.
- [x] Premier backend de comptes Cloudflare, avec tests d'authentification et de base D1.
- [ ] Finaliser et tester les connexions persistantes sur les trois appareils.
- [ ] Achever la synchronisation des profils, bibliothèques, historiques et préférences, y compris la TV.
- [ ] Valider partout les photos, GIF animés et vidéos des profils ; activer R2 uniquement si souhaité.
- [ ] Confirmer sur appareils physiques Android TV le lecteur, les sources, l'audio, les sous-titres et les mises à jour malgré les tests automatisés réussis.
- [ ] Garantir signature Android durable, migration sans perte de données et mises à jour installables.
- [ ] Finaliser les audits de reproductibilité des composants natifs et d'indépendance réseau.

## 🔐 Indépendance, confidentialité et licences

**L'objectif est de ne dépendre d'aucun service Nuvio pour exploiter Kovelyx.** Les sources, distributions et premières infrastructures sont hébergées sous Kovelyx. L'indépendance intégrale du fonctionnement reste à certifier : des modules historiques, protocoles compatibles et composants natifs subsistent encore. Les API tierces telles que TMDB, Trakt, MDBList, les extensions ou les fournisseurs de lecture restent des dépendances distinctes.

Kovelyx dérive de logiciels sous **GNU GPLv3**. Les notices, crédits d'auteurs et obligations de licences sont conservés conformément aux licences applicables ; les supprimer ne renforcerait pas l'indépendance technique.

[**Provenance et licences**](./docs/PROVENANCE.md) · [**Confidentialité**](https://anderson76389.github.io/Kovelyx/confidentialite.html) · [**Conditions d'utilisation**](https://anderson76389.github.io/Kovelyx/conditions.html) · [**Contrôles d'indépendance**](./docs/CONTROLES_INDEPENDANCE.md)

---

<div align="center">

<img src="./assets/brand/kovelyx-logo.svg" alt="Logo Kovelyx" width="78">

### KOVELYX

**Windows · Android · Android TV**

*Votre univers multimédia, sur tous vos écrans.*

[Site officiel](https://anderson76389.github.io/Kovelyx/) · [Téléchargements](https://github.com/anderson76389/Kovelyx/releases) · [Problèmes et suggestions](https://github.com/anderson76389/Kovelyx/issues) · [Retour en haut ↑](#)

</div>
