# Kovelyx pour Windows 10 et 11

**Votre univers multimédia, sur PC.** Kovelyx est une application open source développée avec Kotlin Multiplatform et Compose Desktop. Windows x64 est la seule version PC activement maintenue. Les versions Android mobile et Android TV sont maintenues dans ce même dépôt.

## Télécharger et tester

[**Compilations Windows**](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) · [Releases](https://github.com/anderson76389/Kovelyx/releases)

Une compilation réussie fournit trois formats dans le même artefact **Kovelyx-Windows-installers-experimental** :

| Fichier | Utilisation |
|---|---|
| `Kovelyx-Windows-Setup-….exe` | Un seul fichier pour **installer** Kovelyx avec son runtime Java |
| `Kovelyx-Windows-….msi` | Installateur MSI Windows |
| `Kovelyx-Windows-Portable-….zip` | Dossier portable à extraire entièrement, avec `Kovelyx.exe`, `app` et `runtime` |


### Comptes et synchronisation

**Paramètres → Suivi** contient les cartes **Trakt et MDBList**. Les cartes de connexion utilisent les flux d’autorisation des fournisseurs : code d’appareil, navigateur, synchronisation et déconnexion. Sous Windows, le navigateur par défaut est ouvert lorsque le fournisseur a réellement transmis son URL d'autorisation. Une carte dont l'identifiant OAuth est manquant est volontairement désactivée : afficher un formulaire fictif ne connecterait aucun compte.

Pour les distributions personnalisées, la configuration peut utiliser `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET` et `MDBLIST_CLIENT_ID` dans `apps/desktop/local.properties` avant compilation (ne jamais publier ce fichier dans Git). Les valeurs sont indispensables aux API des fournisseurs et ne peuvent pas être fabriquées par Kovelyx.

**Compte Kovelyx :** la création d'un seul compte synchronisé entre Windows, Android et TV nécessite un serveur de comptes Kovelyx opérationnel. Le backend utilisé dépend du Worker Cloudflare configuré à la compilation. Le mode invité conserve les données locales ; il ne synchronise pas les profils entre appareils.

Le **Setup.exe est autonome pour l'installation** : ce n'est pas un exécutable portable à lancer sans installation. Aucun fichier n'est annoncé disponible avant la réussite des vérifications et son enregistrement dans les artefacts.

## Photos et fonds de profil personnels

Dans **Profils → Modifier / Ajouter** sur Windows :

1. Cliquer sur **Choisir une photo depuis mon PC** pour importer un avatar.
2. Cliquer sur **Choisir un fond depuis mon PC** pour importer le fond de sélection de profil.
3. Vérifier l'aperçu et cliquer sur **Enregistrer**.

Les images PNG, JPG, GIF (première image) et BMP sont acceptées, dans la limite de **12 Mo et 32 mégapixels**. L'avatar est optimisé en PNG, le fond en JPEG. Les fonds **personnels** sont gratuits ; les fonds des catalogues réservés aux membres ne sont pas débloqués.

Les copies sont conservées dans `%APPDATA%\Kovelyx\profile-media`. Tu peux déplacer ou supprimer l'image d'origine après importation. Les chemins de photos locales ne sont **pas envoyés au serveur** : ils ne sont pas automatiquement partagés avec tes autres appareils.

## Vérifications

GitHub Actions compile le MSI, le Setup EXE, l'archive portable, contrôle leurs fichiers puis exécute **les tests Desktop** avant d'enregistrer le paquet. Un échec bloque la distribution de la révision concernée. Ces tests ne remplacent pas les essais interactifs de lecture, navigation, extensions et profils sur un vrai PC.

## Construire depuis les sources

Environnement local : Java 21, Python 3.12, Visual Studio C++, WebView2 SDK et bibliothèques vidéo natives prévues par [le workflow](../../.github/workflows/windows.yml).

```powershell
cd apps/desktop
.\gradlew.bat :composeApp:packageReleaseMsi :composeApp:packageReleaseExe :composeApp:createReleaseDistributable --no-daemon
```

## Provenance et indépendance

Kovelyx s'appuie historiquement sur du code de NuvioDesktop publié sous **GPLv3**. Les crédits et obligations de licences des composants doivent être respectés. L'indépendance complète des services et binaires d'origine n'est **pas encore certifiée** : voir [l'état technique](../../docs/ETAT_TECHNIQUE.md), [l'audit des connexions](../../docs/AUDIT_NUVIO.md) et [la provenance](../../docs/PROVENANCE.md).
