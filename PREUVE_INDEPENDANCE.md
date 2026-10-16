# Kovelyx — dossier de preuve d'indépendance vis-à-vis de Nuvio

Ce dossier sert à **mesurer** l'indépendance de trois applications. Il ne suffit pas de supprimer les noms « Nuvio » des menus et du code, ni de compiler un APK pour déclarer 100 % de réussite.

## Résultats factuels de l'audit au 16 octobre 2026 (hors comptes)

Ces résultats sont conservés avec le numéro d'exécution afin de ne pas confondre preuve historique et état d'une nouvelle compilation.

| Vérification | Résultat obtenu | Preuve / limite |
|---|---|---|
| Vérification statique des sources actives et Gradle (2 646 fichiers examinés lors du contrôle initial) | Réussie pour les URL Nuvio explicites hors compte. Une ancienne URL était utilisée uniquement comme **valeur à rejeter** dans la détection de serveur, sans requête réseau. | [Exécution des tests](https://github.com/anderson76389/Kovelyx/actions/runs/38821480220). Ne couvre pas le code natif, les URLs reconstruites ni les autres formats de ressources. |
| Android mobile, compilation avec `nuvio.tv` et sous-domaines connus bloqués | **Réussie** | [Compilation et filtrage](https://github.com/anderson76389/Kovelyx/actions/runs/38821542695). Les dépendances Kotlin/Android et AAR nécessaires à ce build étaient accessibles sans NuvioMedia. |
| Android TV, compilation avec blocage Nuvio | **Réussie lors de deux exécutions** | [Compilation TV](https://github.com/anderson76389/Kovelyx/actions/runs/38821480220). Une nouvelle exécution peut être en cours ; consulter son résultat séparément. |
| Windows, tests Desktop avec blocage Nuvio | **Réussis après fourniture du SDK WebView2 Microsoft** | [Workflow Windows corrigé](https://github.com/anderson76389/Kovelyx/actions/workflows/prove-upstream-independence.yml). Ne constitue pas une preuve de lancement d'un EXE complet dans le banc réseau. |
| Copie des sources | **Vérifiée dans le dépôt** | Les trois applications et les sources du moteur sont présentes dans le monorepo, sans gitlink actif. |
| Bibliothèques Android natives | **Copies AAR locales vérifiées dans les trois sources d'applications** | Une suppression des Releases amont ne supprime pas ces fichiers du dépôt Kovelyx ; la reproduction exacte de chaque AAR depuis ses sources est une vérification distincte, non acquise. |
| Lecteur Windows natif | **DLL du moteur conservée localement + MPV restauré exclusivement depuis une Release Kovelyx vérifiée par SHA-256** | Une sauvegarde séparée de cette Release reste nécessaire pour se protéger aussi d'une panne ou suppression du dépôt Kovelyx. |
| Reconstruction P2P complète | **Test dédié ajouté ; validation à consulter dans Actions** | [Tests du moteur](https://github.com/anderson76389/Kovelyx/actions/workflows/engine-source-test.yml). Le test précédent sans libtorrent ne prouvait pas la reconstruction du moteur P2P complet. |
| Suppression des téléchargements d'origine dans les workflows actifs de sauvegarde | **Réalisée** | Les workflows d'import du moteur et des trois applications vérifient maintenant les **copies déjà présentes** au lieu de faire un `git fetch` chez NuvioMedia. |
| Test runtime sans accès à Nuvio pour lecture, catalogue, profils locaux, addons et débridage | **Non réalisé exhaustivement** | Nécessite des scénarios sur les trois appareils et capture du trafic sortant, avec blocage de domaines/URLs effectif. |

**Ce que cela prouve déjà :** si l'organisation NuvioMedia supprime ses sources et domaines, les copies et binaires *déjà archivés* dans Kovelyx ne disparaîtront pas par effet automatique. Les builds mobiles et TV ont été réalisés sous blocage des domaines connus.

**Ce que cela ne prouve pas encore :** une disparition de Nuvio ne pourrait jamais affecter un chemin réseau caché dans une bibliothèque native ou un service non couvert par les tests. Il faut aussi reconstruire les lecteurs natifs intégraux et valider les mises à jour et la reprise des données.

### Références Nuvio qui ne sont PAS des dépendances réseau

Les packages historiques `com.nuvio.*`, chemins de cache `nuvio-engine`, licences GPL et documents `docs/PROVENANCE.md` servent à la compatibilité et à l'attribution. Les renommer sans migration pourrait faire perdre des réglages et casser l'interface Java/native. La présence d'un lien vers les sources historiques dans une attribution ne signifie pas que Kovelyx télécharge ces sources.

Des fichiers de distribution iOS historiques tels que `apps/mobile/store.json` ou `apps/desktop/store.json` peuvent encore référencer de vieilles IPA de Nuvio : ils **ne constituent pas** une source de mise à jour des APK Android ni de l'EXE Windows. Ils ne doivent pas être présentés comme une distribution iOS Kovelyx. Le périmètre actuel est Windows/Android mobile/Android TV.

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
