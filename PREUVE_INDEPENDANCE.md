# Kovelyx — dossier de preuve d'indépendance vis-à-vis de Nuvio

Ce dossier sert à **mesurer** l'indépendance de trois applications. Il ne suffit pas de supprimer les noms « Nuvio » des menus et du code, ni de compiler un APK pour déclarer 100 % de réussite.

## Définition précise

La disparition de `nuvio.tv`, `api.nuvio.tv`, des autres serveurs NuvioMedia et de leurs dépôts et Releases **ne doit pas empêcher** Kovelyx de :

- démarrer, afficher ses menus et sauvegarder ses paramètres locaux ;
- utiliser ses catalogues et extensions configurés, sous réserve que **leurs propres services** soient accessibles ;
- consulter TMDB et les services externes autorisés avec des identifiants fournis par l'utilisateur ;
- lire des fichiers locaux, ou des sources légalement accessibles via leurs propres serveurs, avec le lecteur intégré ;
- effectuer la recherche, le suivi local, les téléchargements, la reprise et la gestion des profils sans compte Nuvio ;
- télécharger les nouvelles versions uniquement depuis `anderson76389/Kovelyx` ;
- être **reconstruit** à partir du code et des dépendances sauvegardés, sans re-télécharger le code ou les binaires de NuvioMedia.

**Exception transitoire explicite :** les anciens comptes et la synchronisation Nuvio. Leur indépendance n'est pas certifiée et sera traitée avec le futur système de comptes Kovelyx. Une authentification via Trakt/MDBList n'est pas un compte Nuvio : elle doit être vérifiée séparément.

## Preuves automatisées sur GitHub

Workflow : [Prouver l'indépendance de compilation sans serveurs Nuvio](../.github/workflows/prove-upstream-independence.yml)

| Contrôle | Preuve apportée | Ce qu'il ne prouve pas |
|---|---|---|
| Sources de production | Signale les URL NuvioMedia explicites dans le code Kotlin/Java actif et les configurations Gradle des trois applications, sauf l'ancienne connexion de comptes identifiée | URL créées dynamiquement, SDK externes, ressources non ciblées, binaires natifs |
| Compilation Android | Construit l'APK mobile après redirection locale des domaines Nuvio et redirection des clones Git vers NuvioMedia | Comportement de chaque menu, vidéo ou service à l'exécution |
| Compilation TV | Même procédure pour `assembleFullDebug` de la TV | Compatibilité télécommande, codecs et lecture sur TV Box réelle |
| Tests Windows | Lance les tests Kotlin desktop après blocage local des domaines historiques | Installation/lecture de l'EXE complet, tous les codecs et plugins |
| Workflow « tests Kotlin complets » | Identifie les régressions des traitements internes | OAuth, stabilité de tous les services distants, vidéo sur matériel |

Le blocage `/etc/hosts` ou `hosts` ne bloque **que les noms connus** ; d'autres sous-domaines, l'API GitHub ou des adresses directes ne sont pas tous interceptés. Le routage Git global vers un hôte invalide interdit les clones Git usuels de l'organisation historique, **mais ne remplace pas un proxy réseau à filtrage de chemin**. Même une réussite du workflow n'est donc **pas** une preuve absolue de toute absence d'appel réseau.

Ne pas affirmer qu'une étape a réussi tant que GitHub Actions ne l'indique pas sur le **commit concerné**.

## Protocole de test sur installations réelles

Réaliser un test par plateforme avec une version identifiable (hash du commit + version téléchargée) et un **profil local de test**, sans compte Nuvio.

1. **Préparer l'environnement.** Autoriser Internet pour les services tiers réellement utilisés (TMDB, API et extensions configurées). Bloquer tous les domaines Nuvio connus via une règle DNS/pare-feu locale ou réseau, journalisée. Ne pas bloquer `github.com` en entier : cela empêcherait aussi les mises à jour Kovelyx.
2. **Observer les connexions.** Capturer DNS, connexions TCP, noms TLS/SNI et les URL HTTP accessibles depuis le proxy réseau du banc de test. Ajouter explicitement une règle de rejet des chemins GitHub `/NuvioMedia/` et `/repos/NuvioMedia/` si le proxy le permet. Les URL HTTPS ne sont pas visibles dans une simple capture réseau chiffrée.
3. **Exécuter la matrice.** Démarrage sans compte, navigation dans chaque menu, langue française, recherche et fiches, import d'une collection, extensions, affiches, lecture vidéo locale et distante autorisée, audio/sous-titres, avance/reprise, profils, favoris, cache, téléchargement, sortie et redémarrage. Sur TV : télécommande/D-pad et Google TV/TV Box. Sur mobile : rotation et reprise. Sur Windows : installation EXE, lecteur natif, mises à jour.
4. **Tester les échecs.** Couper Nuvio en premier ; puis, **séparément**, couper un seul service tiers (ex. TMDB) pour vérifier les erreurs et modes de repli. Ne pas appeler « bug Kovelyx » une interruption volontaire de l'API externe.
5. **Tester les mises à jour.** Vérifier le dépôt GitHub réellement contacté, l'APK/EXE sélectionné, sa signature, son installation et la préservation des paramètres. Les APK Debug ne garantissent pas une mise à jour sans désinstallation.
6. **Reconstruire sur une machine propre.** Supprimer tous les caches Gradle/Maven/Actions ; isoler NuvioMedia ; reconstruire EXE, APK mobile et APK TV avec les sources, AAR/JAR, DLL et dépendances archivés. La simple présence d'une DLL ou d'un AAR local **ne prouve pas** sa reconstruction depuis le code source.
7. **Conserver une preuve falsifiable.** Pour chaque scénario, garder le commit testé, le nom et SHA-256 de l'artefact, le résultat, les erreurs et une capture du trafic nettoyée de tout jeton/cookie/clé API. Un résultat non exécuté doit être noté « non testé », jamais « réussi ».

### Critères de décision

- **ÉCHEC :** toute dépendance indispensable à un domaine ou téléchargement de Nuvio hors exception de compte, tout build qui doit accéder à NuvioMedia, ou une fonction principale cassée sans cette dépendance.
- **PARTIEL :** analyse statique et compilations sans Nuvio réussies, mais tests réseau/lecteur/installation ou reconstruction native manquants.
- **VALIDÉ :** toutes les trois versions satisfont les scénarios et reconstruisent les dépendances nécessaires **sans Nuvio**, avec traces et résultats reproductibles.
- **COMPTES :** validation distincte, reportée ; on ne doit pas annoncer une synchronisation Kovelyx existante avant son déploiement.

## Travaux bloquants encore identifiés

Les tests Kotlin élargis ont révélé au moins neuf échecs Android mobile (mise à jour, progression de lecture). Des sélecteurs de Releases APK doivent être adaptés aux préversions `android-preview-<sha>` et `tv-preview-<sha>` avant de promettre des mises à jour fiables. AllDebrid prioritaire, Real-Debrid secondaire et la parité des profils locaux restent des fonctionnalités à mettre en œuvre ou vérifier sur chaque plateforme.

Pour connaître **l'état réel le plus récent**, utiliser les pages Actions, non les assertions historiques de ce document.

- [Preuves par blocage des serveurs Nuvio](https://github.com/anderson76389/Kovelyx/actions/workflows/prove-upstream-independence.yml)
- [Suites de tests unitaires Android et TV](https://github.com/anderson76389/Kovelyx/actions/workflows/functional-verification.yml)
- [Tests et builds Android](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)
- [Compilation et publication Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
