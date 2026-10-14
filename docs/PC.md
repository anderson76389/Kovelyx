# 🖥️ Guide PC — Kovelyx Desktop

Ce guide concerne **uniquement Windows et Linux**. Les anciennes sources Android et TV sont conservées dans Git pour ne pas perdre l'historique, mais elles ne font pas partie de la priorité actuelle.

## Quel fichier choisir ?

| Situation | Fichier conseillé | Avantage |
|---|---|---|
| Installer facilement sur Windows 10/11 x64 | `Kovelyx-Windows-Setup-….exe` | **Un fichier** à lancer pour installer Kovelyx |
| Installation MSI traditionnelle | `Kovelyx-Windows-….msi` | Paquet standard Windows |
| Garder une copie sans installation classique | `Kovelyx-Windows-Portable-….zip` | Dossier portable, mais il contient **de nombreux fichiers nécessaires** |
| Utiliser Linux x86-64 sans installation | `Kovelyx-Linux-x86_64-….AppImage` | **Un seul fichier exécutable** ; lancement sans installateur classique |
| Installer sur Debian / Ubuntu x86-64 | `Kovelyx-Linux-amd64-….deb` | Paquet système et dépendances déclarées |
| Installer sur Fedora x86-64 | `Kovelyx-Linux-x86_64-….rpm` | Paquet système et dépendances déclarées |

**Un seul fichier téléchargé ne veut pas dire un seul fichier utilisé en mémoire** : le runtime Java, les extensions natives et les bibliothèques restent nécessaires. Le `Setup.exe` les installe automatiquement. Une AppImage les rassemble autant que possible mais peut encore dépendre de bibliothèques Linux du système.

## Où récupérer les fichiers ?

- [Build Windows validé (EXE, MSI, portable)](https://github.com/anderson76389/Kovelyx/actions/runs/38509512148/artifacts/11869860908)
- [Compilations Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
- [Build Linux validé (AppImage x86-64)](https://github.com/anderson76389/Kovelyx/actions/runs/38510728469/artifacts/11869259804)
- [Compilations Linux : AppImage + job DEB/RPM](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml) — les paquets natifs ne sont disponibles qu'après réussite du nouveau job
- [Releases](https://github.com/anderson76389/Kovelyx/releases) — uniquement les versions effectivement publiées
- [Site Kovelyx](https://anderson76389.github.io/Kovelyx/)

Les artefacts Actions ne sont pas des Releases publiques et **peuvent expirer**. Le paquet de test doit provenir d'une compilation verte. Les fichiers ZIP téléchargés depuis l'interface Actions sont des **conteneurs de téléchargement** : il faut d'abord les ouvrir pour obtenir l'EXE ou l'AppImage.

## Première ouverture sur Windows

1. Installer via `Kovelyx-Windows-Setup-….exe` (ou choisir la version portable et extraire tous ses fichiers).
2. Vérifier le nom Kovelyx dans le menu Démarrer et l'icône.
3. Ouvrir l'application en mode invité si aucun serveur de compte n'est configuré.
4. Créer un profil et tester une image depuis **Profils → Modifier → Choisir une photo depuis mon PC**.
5. Tester un fond depuis le même écran ; sauvegarder et redémarrer pour vérifier la persistance.
6. Ajouter les extensions **que vous êtes autorisé à utiliser**, essayer une vidéo, les pistes et les sous-titres.

Les photos personnelles sont copiées dans `%APPDATA%\Kovelyx\profile-media` et ne sont pas partagées automatiquement avec un compte en ligne.

## Connexion simplifiée et métadonnées en français

**Sur Kovelyx PC, SIMKL n'est plus proposé dans la connexion ni dans les choix des sources de suivi.** Les anciennes données sont conservées sur le disque pour éviter toute perte.

**Trakt et MDBList :** les boutons de connexion peuvent ouvrir leur page d'autorisation dans le navigateur. Une application OAuth **Kovelyx** doit cependant être enregistrée auprès de ces services pour obtenir un identifiant d'application valide. Tant que Kovelyx ne dispose pas d'un identifiant public officiellement enregistré, une configuration initiale reste nécessaire via **Configurer les clés Trakt et MDBList**. Cela n'est **pas** le mot de passe de l'utilisateur : on ne peut pas remplacer ces identifiants par ceux d'une autre application. Une fois configuré, un clic sur **Connecter Trakt** ouvre la page d'activation après réception du code. MDBList ouvre sa page de validation quand il retourne une URL de confirmation.

**Descriptions des films et séries :** les nouvelles installations Windows utilisent désormais `fr-FR` comme langue de métadonnées TMDB. Les anciens profils en anglais sont migrés une seule fois ; modifier la langue ensuite reste possible. Dès qu'une clé TMDB valable est renseignée dans **Paramètres → TMDB**, l'enrichissement est activé sur PC et les fiches sont rechargées lors des changements de langue ou de clé. Cela permet de remplacer les résumés anglais provenant de certaines extensions par la description française TMDB **quand TMDB en possède une**. Si aucune traduction française n'existe, ou si l'extension ne fournit pas d'identifiant TMDB/IMDb exploitable, le résumé d'origine peut encore s'afficher. Aucun texte n'est inventé ou traduit automatiquement.

## Configurer Trakt et MDBList sur Windows

Dans **Paramètres → Suivi → Configurer les clés Trakt et MDBList**, renseignez les identifiants OAuth de vos propres applications :

- **Trakt :** `Client ID` et `Client Secret` créés depuis [Trakt — OAuth Applications](https://trakt.tv/oauth/applications). La version Windows emploie l'autorisation par code affiché à l'écran.
- **MDBList :** `Client ID` de votre application MDBList. La connexion utilise aussi un code d'autorisation.

Cliquez sur **Enregistrer**, puis **Connecter Trakt** ou **Connecter MDBList**, et autorisez Kovelyx dans le navigateur. Sans identifiants, les boutons restent désactivés : le code ne peut pas inventer de clés valides. Le site web de Ma-Collection n'est pas nécessaire pour connecter ces services.

Les valeurs sont mémorisées **localement sur votre PC** dans le stockage de Kovelyx, pas dans GitHub. Actuellement, elles ne sont **pas chiffrées** : évitez de les utiliser pour des services sensibles ou de partager ce dossier. Ce sont des identifiants d'application, pas votre mot de passe Trakt/MDBList. Vous pouvez les effacer dans le même écran. Les connexions réseau et permissions réelles doivent être testées avec votre compte.

Les crédits du code d'origine restent documentés dans [PROVENANCE.md](PROVENANCE.md) pour respecter les licences. L'identité et le lien de l'application publiés dans Kovelyx PC sont ceux de [Kovelyx](https://github.com/anderson76389/Kovelyx).

## Sécurité des téléchargements

Chaque artefact généré par le workflow contient des fichiers `.sha256`. Avant d'installer une archive téléchargée, vérifier son origine GitHub et comparer les empreintes :

```powershell
Get-FileHash -Algorithm SHA256 .\Kovelyx-Windows-Setup-*.exe
```

Les exécutables expérimentaux peuvent être **non signés**. Cela peut déclencher un avertissement SmartScreen ; cela ne prouve ni qu'ils sont sûrs ni qu'ils sont malveillants. Ne désactivez pas les protections Windows pour tester.

## Mise à jour depuis l'application

Le bandeau de mise à jour apparaît automatiquement lorsqu'une Release GitHub plus récente **et compatible avec votre format d'installation** est disponible. Cliquez une fois sur « Mettre à jour » : Kovelyx télécharge et vérifie le paquet, puis lance automatiquement la mise à jour adaptée à votre installation.

| Installation actuelle | Comportement prévu |
|---|---|
| Windows avec Setup.exe / MSI | Un seul clic : télécharge et vérifie le MSI officiel, ferme Kovelyx, installe sans assistant interactif, puis relance l'application (Windows peut demander l'autorisation administrateur) |
| Windows portable (ZIP) | Prépare et remplace les fichiers après fermeture, en conservant une sauvegarde du dossier précédent |
| Linux AppImage | Remplace le fichier après fermeture, avec possibilité de conserver une sauvegarde |
| Linux DEB / RPM | Ouvre le gestionnaire de paquets du système (validation éventuelle de l'administrateur) |
| Linux Flatpak | Mises à jour gérées par Flatpak ; pas d'installation directe dans Kovelyx |

Toutes les versions publiées doivent avoir un **nom de version identique dans l'application et sur GitHub** ainsi qu'une empreinte `.sha256` correspondante. Si le paquet ou sa signature de contrôle ne correspond pas, l'installation est bloquée. Le processus ne désinstalle pas intentionnellement les profils ou les paramètres locaux.

**Ce n'est pas un téléchargement par différences** : le nouveau paquet complet est téléchargé en arrière-plan depuis Kovelyx. Les formats de mise à jour et le redémarrage doivent être testés sur Windows et plusieurs distributions Linux. Les copies de sauvegarde portables prennent de l'espace disque.

## Données et mises à jour

- **Paramètres Windows :** `%APPDATA%\Kovelyx`
- **Cache Windows :** `%LOCALAPPDATA%\Kovelyx\Cache`
- **Images personnelles Windows :** `%APPDATA%\Kovelyx\profile-media`

Faire une **copie** de ces données avant un changement important de version. Un `Setup.exe` ne garantit pas à lui seul un mécanisme de mise à jour automatique : la logique de mise à jour doit être testée.

## Tester et signaler

Utiliser le [plan de vérification PC](./TESTS_PC.md). Pour chaque anomalie, joindre la version du fichier, les étapes de reproduction, le résultat attendu, le résultat constaté et les journaux non sensibles. La compilation verte n'est **pas** une certification de toutes les fonctionnalités.

## Indépendance et licences

Le code est dérivé de NuvioDesktop (GPLv3). Certains binaires natifs et liens hérités restent à auditer. Voir [provenance](./PROVENANCE.md) et [audit des dépendances](./AUDIT_DEPENDANCES.md). Le logiciel n'inclut pas de contenus tiers.
