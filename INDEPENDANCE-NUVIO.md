# Indépendance de Kovelyx vis-à-vis de Nuvio — état contrôlé le 14 octobre 2026

## Objectif

Si les dépôts et serveurs de Nuvio disparaissent, le client **Kovelyx Windows** doit pouvoir continuer à démarrer, se mettre à jour depuis son dépôt et être reconstruit, sans télécharger de fichiers depuis les dépôts Nuvio. Cela ne signifie pas que tous les fournisseurs de métadonnées, addons, API et contenus externes fonctionnent hors ligne.

## Ce qui est déjà indépendant de GitHub Nuvio

- Les sources de l'application Windows sont copiées dans `apps/desktop`, dans **ce dépôt**.
- Les installateurs et les mises à jour Windows sont diffusés depuis les **Releases de `anderson76389/Kovelyx`**.
- Les sources du moteur `vendor/engine` sont archivées dans **ce dépôt**. Les fichiers d'attribution et leur licence doivent être conservés.
- Le workflow Windows recompile le **JAR JVM du moteur** depuis `vendor/engine/platform/jvm` et vérifie la présence des classes attendues.
- Le runtime vidéo `libmpv-2.dll`, trop volumineux pour le dépôt Git classique, peut être récupéré **depuis nos propres ZIP portables Kovelyx**, via `scripts/restore-windows-mpv.ps1`, avec contrôle strict du SHA-256 et de la taille. Le workflow `.github/workflows/native-windows.yml` ne clone plus de dépôt Nuvio ; `.github/workflows/windows.yml` dispose de cette restauration en secours si l'artefact temporaire expire.
- Un test GitHub Actions de cette restauration sans Nuvio a réussi : https://github.com/anderson76389/Kovelyx/actions/runs/38633840172.

## Ce qui n'est PAS encore démontré comme totalement autonome

1. **Lecture multimédia réelle :** les DLL embarquées sont conservées depuis les distributions existantes, mais `libmpv-2.dll` n'est **pas encore reconstruite de manière reproductible depuis le code source**. Son SHA-256 prouve l'intégrité de la copie, pas l'adéquation des sources et licences. Préserver le code source correspondant, les notices et les options de compilation reste indispensable.
2. **Le moteur P2P natif :** ses sources sont archivées, mais l'identité exacte entre les DLL historiques et les sources sauvegardées n'a pas été certifiée. Une reconstruction complète, tests P2P inclus, reste nécessaire.
3. **Compte unique/synchronisation :** aucun backend hébergé Kovelyx n'est encore déployé. Il s'agit d'un chantier séparé, reporté volontairement.
4. **Métadonnées, catalogue et lecture :** les films et séries dépendent des addons installés et de fournisseurs tiers comme TMDB, Cinemeta ou les autres services configurés. L'erreur « Échec du chargement » signalée sur les fiches/lectures n'est **pas** résolue par cet audit : il faut la diagnostiquer et tester dans le vrai Windows EXE.
5. **Connexions Trakt / MDBList :** ce sont des API tierces. Leur connexion peut nécessiter des identifiants OAuth valides enregistrés pour Kovelyx ou configurés par l'utilisateur. Aucun identifiant Nuvio ne doit être réutilisé.
6. **Code Android et TV :** conservé dans ce monodépôt pour le travail futur, mais son indépendance, ses manifestes, sa signature et ses mises à jour exigent **un audit et une compilation spécifiques**.
7. **Test d'indisponibilité réelle :** il manque encore un test automatisé/exécuté bloquant tous les domaines Nuvio pendant une session complète (démarrage, recherche, fiche, lecture, mises à jour, authentification facultative).

## Références Nuvio qui peuvent rester sans dépendance serveur

Les espaces de noms historiques `com.nuvio.app`, les noms d'artefacts natifs, certaines clés de migration et les textes de licence ne contactent pas Nuvio par eux-mêmes. **Ne pas renommer aveuglément** ces symboles : cela peut casser la compilation, les données, les liens ou les clients futurs. Les attributions légales doivent rester accessibles.

La découverte de serveurs personnalisés utilise encore le protocole historique `/.well-known/nuvio` et reconnaît sa valeur `service`. Cela n'est pas un appel au serveur Nuvio lui-même : le client interroge **l'adresse personnalisée saisie par l'utilisateur**. Le futur backend Kovelyx devra définir son propre protocole, avec migration explicite des installations existantes.

## Conclusion de contrôle

**Dépendance directe au GitHub Nuvio pour reconstruire Windows : supprimée dans les workflows de sauvegarde et de restauration MPV, sous réserve que les Releases Kovelyx restent disponibles.** La reconstruction complète à partir des seuls codes sources, la conformité des composants natifs et le fonctionnement de toutes les fonctionnalités en situation réelle ne sont **pas encore certifiés**.

Les autres dépendances (GitHub Kovelyx, bibliothèques Gradle/Maven, services tiers, addons) ne sont pas des dépendances à Nuvio, mais peuvent tomber en panne indépendamment.

Cet inventaire évite de confondre « nous possédons une copie » et « toute l'application est vérifiée 100 % indépendante ».