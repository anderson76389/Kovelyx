# Kovelyx — dossier de preuve d'indépendance vis-à-vis de Nuvio

Ce dossier sert à **mesurer** l'indépendance de trois applications. Il ne suffit pas de supprimer les noms « Nuvio » des menus et du code, ni de compiler un APK pour déclarer 100 % de réussite.

## Résultat du contrôle DNS relancé le 16 octobre 2026

**Réussite des deux émulateurs Android, mobile et TV**, dans [l'exécution 38922387126](https://github.com/anderson76389/Kovelyx/actions/runs/38922387126) : les deux jobs ont téléchargé leurs APK depuis les propres Releases Kovelyx, attendu la disponibilité du DNS tiers, confirmé par une **requête DNS surveillée** que les sous-domaines Nuvio étaient filtrés, puis installé, lancé et navigué dans l'application. Les journaux indiquent l'absence de nouveaux accès DNS vers Nuvio durant la navigation et l'absence de crash sur la période contrôlée.

- APK mobile contrôlée : `android-preview-d863d3012c93`.
- APK TV contrôlée : `tv-preview-58bf54ff901a`.
- Windows : le test de lancement de la distribution portable sous blocage Nuvio est réussi dans [38918493653](https://github.com/anderson76389/Kovelyx/actions/runs/38918493653).
- Reconstruction autonome déjà obtenue : **JAR JVM** et **cœur C++ Linux avec P2P/libtorrent actif** ; voir [38920193102](https://github.com/anderson76389/Kovelyx/actions/runs/38920193102) pour le statut du dernier ensemble de jobs.
- Limites : ces tests de démarrage et navigation **ne couvrent pas** toutes les lectures, codecs, extensions, API protégées, Trakt/MDBList et débridage ; la reproduction de la DLL Windows P2P intégrale et des AAR Android multi-ABI depuis des sources reste à prouver. Les comptes historiques sont volontairement exclus.
- Mise à jour : l'updater Windows pointe uniquement sur `anderson76389/Kovelyx`, vérifie l'URL d'origine GitHub et l'empreinte SHA-256 ; toutefois la publication d'une **nouvelle** Release Windows a rencontré un HTTP 403 en [38921809378](https://github.com/anderson76389/Kovelyx/actions/runs/38921809378). Le secours conserve les installateurs vérifiés en artefact éphémère ; les droits GitHub de publication doivent être corrigés pour reprendre l'auto-publication.

**Conclusion précise :** la disparition des domaines et dépôts de Nuvio ne bloque pas le démarrage et la navigation basique testés de ces trois applications. Ne pas en déduire une garantie totale pour chaque service, extension, moteur natif et future mise à jour.

## Résultats factuels de l'audit au 16 octobre 2026 (hors comptes)

### Complément : tests du moteur natif et lancement des exécutables

| Preuve | Résultat constaté | Exécution |
|---|---|---|
| Moteur P2P **Linux partagé** avec libtorrent et Boost, versions épinglées, compilé à partir de `vendor/engine` et avec les dépôts Nuvio interdits | **Réussi**, après correction `CMAKE_POSITION_INDEPENDENT_CODE=ON` nécessaire pour libtorrent statique lié dans une bibliothèque partagée | [38917131987](https://github.com/anderson76389/Kovelyx/actions/runs/38917131987) |
| Inspecter les DLL, JAR, AAR et membres natifs locaux des trois applications pour les anciennes URL Nuvio/NuvioMedia en clair | **Réussi : aucun endpoint historique en clair** dans les fichiers inclus examinés | [38917898204](https://github.com/anderson76389/Kovelyx/actions/runs/38917898204) |
| Télécharger la version **Windows portable publiée**, bloquer `nuvio.tv` et ses sous-domaines dans Windows, puis lancer le vrai EXE | **Réussi : processus démarré et encore vivant après 25 secondes**, avec résolution GitHub non bloquée | [38918493653](https://github.com/anderson76389/Kovelyx/actions/runs/38918493653) |
| Android mobile + Android TV : APK **publiés**, émulateurs filtrant le DNS Nuvio, navigation basique, vérification du processus, captures et journal DNS | **Test de contrôle ajouté**. Vérifier le résultat du run le plus récent dans Actions ; les anciennes exécutions avaient des erreurs de banc DNS, pas une preuve de crash applicatif | [Workflow Android runtime](https://github.com/anderson76389/Kovelyx/actions/workflows/runtime-blackout.yml) |

Attention : le moteur P2P testé est la cible **Linux**. La DLL Windows P2P complète et les AAR Android de toutes les architectures ne sont pas encore reconstruits depuis le code source dans ce test, même si leurs copies binaires nécessaires aux applications sont déjà conservées chez Kovelyx. Un test de lancement sans compte ne couvre ni toutes les extensions ni toutes les vidéos et API protégées. Il faut distinguer ces scénarios pour ne pas annoncer une certification irréaliste.



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

### Contrôle des workflows et des binaires déjà sauvegardés

Le **16 octobre 2026**, les **27 workflows actifs** de `.github/workflows/` ont été examinés pour les URLs explicites NuvioMedia et `nuvio.tv`. Les seules références relevées dans les workflows actifs correspondaient à des **règles qui interdisent volontairement ces domaines pendant les tests**. Les anciens workflows d'import ont été remplacés par des vérifications de fichiers locaux ; ils ne téléchargent plus de sources Nuvio.

Copies locales relevées dans le dépôt :

- `apps/tv/app/libs/` : **15 fichiers**, dont le moteur Android `lib-nuvio-engine-android-0.1.4.aar` (nom historique).
- `apps/mobile/composeApp/libs/` : **13 fichiers**, dont une copie du même moteur Android.
- `apps/desktop/composeApp/libs/` : **14 fichiers**, dont le JAR JVM du moteur.
- `apps/desktop/composeApp/src/desktopMain/native/windows/engine/nuvio_engine.dll` : copie du moteur natif Windows présente.
- `vendor/engine/` : **145 fichiers** pour les sources du moteur, dont CMake, fichiers C++, tests et notices de licence.
- Le lecteur `libmpv-2.dll` (115 320 296 octets) est restauré **depuis les propres Releases Kovelyx**, avec vérification de sa taille et du SHA-256 `07c68bb211f23a218ded0a36eb12207dc3aeb44e5318ffca6ce9dcc7c3173906`.

**Interprétation :** même si NuvioMedia efface ses dépôts, la disparition de l'origine ne supprime pas ces copies stockées chez Kovelyx. Les liens anciens conservés à titre de provenance ne sont pas des liens téléchargés pendant les builds ordinaires. Pour protéger aussi l'hypothèse distincte d'une disparition de GitHub/Kovelyx, conserver **une seconde sauvegarde personnelle des sources, des APK, de l'EXE et de la DLL MPV**.

La disponibilité de fichiers binaires ne prouve toujours pas que chacun peut être reconstruit exactement à partir de sources correspondantes ; le job P2P complet est dédié à cette vérification.

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
