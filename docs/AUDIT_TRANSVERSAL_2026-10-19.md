# Audit transversal Kovelyx — comptes, synchronisation, fonctions et indépendance

Date de revue : **19 octobre 2026**. Périmètre : branche de la PR #40, sans déploiement en production, sans secrets personnels, sans fusion. Ce document est un **état de preuves** et non une certification 100 %.

## Règle de lecture

- **Compilé** : Gradle/Kotlin et packaging ont réussi ; ne garantit ni le premier lancement, ni un bouton fonctionnel.
- **Testé en CI** : cas couverts par des tests explicites, sur environnement contrôlé.
- **Testé en production** : session, données et appareils réels vérifiés sans ambiguïté.
- **Non validé** : le code ou l'interface existe, mais son fonctionnement complet n'est pas prouvé.

## 1. Builds

Sur le commit `b121079d` de la PR #40, les workflows suivants ont été verts :
- Windows : MSI et packaging, exécution `39288430862`.
- Android mobile + Android TV : APK Full Debug, exécution `39288430993`.
- Cloudflare : tests du Worker, contrat et schéma D1, exécution `39288430883`.
- Identité graphique : exécution `39288430958`.
- Mise à jour Windows : exécution `39288431059`.

**Après ces builds**, le code a encore évolué pour empêcher l'usage de l'ancien serveur et ajouter une couverture plus large. Les derniers runs de la branche doivent rester verts avant fusion. Toutes les Release publiques peuvent contenir un commit différent.

## 2. Comptes personnels Kovelyx (Cloudflare Worker + D1)

| Comportement | Windows | Android mobile | Android TV | Preuve |
| --- | --- | --- | --- | --- |
| Formulaire e-mail/mot de passe Cloudflare | Code présent | Code présent | Code présent | Compilation/lecture code |
| Mot de passe authentifié par Better Auth | Client intégré | Client intégré | Client intégré | **Un essai réel avec les données utilisateur n'a PAS eu lieu** |
| Persistance chiffrée de session entre redémarrages | **Non** | **Non** | **Non** | Sessions explicitement en mémoire |
| Création libre de nouveaux comptes | **Non par défaut** | **Non par défaut** | **Non par défaut** | Inscriptions publiques fermées dans le Worker |
| Profils D1 : lire/créer/renommer | Code intégré | Code intégré | Noms et médias en lecture partielle | TV non entièrement migrée |
| Suppression de compte utilisateur Cloudflare | **Non** | **Non** | **Non** | Ne pas confondre suppression de profil et suppression de compte |
| Une seule identité synchronisée après redémarrage sur les 3 appareils | **Non prouvé** | **Non prouvé** | **Non prouvé** | Reconnexion requise, TV incomplète |

Le Worker public déployé a passé son contrôle **non authentifié** : `/health`, `/api/auth/get-session`, refus HTTP 401 sur `/v1/me` sans jeton et HTTP 403 avec une origine non autorisée. Cela ne teste pas le mot de passe ni la base d'un vrai utilisateur.

Le CI comprend désormais un test **Worker + D1 locaux jetables** avec **deux comptes factices** ; la création de compte, la connexion/déconnexion, la lecture de la session, l'isolement des profils, le renommage, les révisions CAS et la pagination sont **réellement passés**. Run [39295543457](https://github.com/anderson76389/Kovelyx/actions/runs/39295543457). Ce test a aussi détecté puis fait corriger le défaut d'en-tête `Origin` manquant sur les applications natives. **Le Worker de production n'a PAS été redéployé avec ces corrections.** Un essai réel du même compte sur PC, mobile et TV reste nécessaire.

## 3. Synchronisation

| Donnée | Windows / Android mobile | Android TV | État réel |
| --- | --- | --- | --- |
| Identité du compte | Better Auth / D1 | Better Auth / D1 | Sessions mémoire, non persistantes |
| Nom/UUID des profils | Import non destructif, écriture D1 | Lecture des noms et correspondance prudente | Pas de mapping automatique complet TV |
| Bibliothèque | Import et mutations D1 avec révisions CAS ; import initial protecteur | **Pas de sync D1 de la bibliothèque** | Parcours réel bidirectionnel non éprouvé |
| Images/GIF/vidéos personnels | Import local et API R2 privée préparée | Téléchargement et rendu conditionnels | **Pas de partage cross-device tant que R2 n'est pas activé** |
| Progression de lecture / épisodes vus | Anciennes voies de synchronisation Supabase/Trakt ; **pas encore migrées intégralement vers D1** | Ancien service de synchronisation Supabase | Non synchronisé nativement de bout en bout sur 3 appareils |
| Paramètres de profil et apparence | Voies historiques Supabase encore présentes | Voies historiques encore présentes | Pas de garantie D1 |
| Extensions et addons installés | Fonctionnement local ; ancienne sync Supabase | Fonctionnement local ; ancienne sync Supabase | Pas de garantie D1 |
| Clés de services / débrideurs | Stockage local ou authentification de fournisseur | Stockage local ou fournisseur | Aucune synchronisation chiffrée universelle établie |
| Favoris/catalogues | Catégories autorisées par contrat serveur | Pas de client D1 complet | L'existence d'une catégorie serveur ne constitue PAS une fonction synchronisée |

Le backend D1 propose des catégories (`library`, `favorites`, `watch_progress`, `catalogues`, `preferences`, `profile_settings`, `addons`, `plugins`) : **les applications ne les implémentent pas toutes**.

## 4. Indépendance Nuvio : situation honnête

**Déjà vérifiable :**
- Les applications portent les identifiants installables Kovelyx, et les mises à jour visent les Releases de ce dépôt.
- Les builds APK Windows/TV/mobile peuvent être produits depuis ce dépôt GitHub ; les tests de compilation ont réussi.
- Le nouveau service Worker D1/Better Auth est sous Kovelyx, et son protocole est séparé de celui de Supabase.
- Les domaines Nuvio explicites sont interdits dans plusieurs parcours actifs par des audits statiques ; le lien de liaison mobile `nuvio.tv/link` est désormais écarté ; en mode Cloudflare, le client Supabase des applications Compose refuse de s'ouvrir.
- Le moteur PC libmpv est embarqué depuis les ressources et procédures de build du dépôt Kovelyx ; provenance et reproductibilité complète restent à maintenir.

**Reste à traiter pour affirmer « aucune dépendance opérationnelle à Nuvio » :**
- Le dépôt contient encore **beaucoup de code et de nomenclature hérités** (`com.nuvio.*`, `nuvio-engine`, compatibilité des stockages et RPC Supabase, métadonnées, traductions, ressources). Une chaîne de package n'est pas un appel réseau, mais l'inventaire doit être conservé.
- Les anciens modules de synchronisation, d'authentification par code et de partage de paramètres n'ont pas tous été réécrits pour D1. En mode Cloudflare, bloquer l'ancien protocole protège des dépendances réseau, **mais ne remplace pas la fonction absente**.
- Le dossier `services/kovelyx-cloud/supabase/` et des fichiers liés à l'ancien protocole restent conservés pour analyse/migration. Leur présence n'est pas une preuve de connexion à Nuvio, ni une preuve de suppression achevée.
- Les composants binaires natifs (AAR, JAR, DLL), ressources GPL/Apache/MIT et licences doivent faire l'objet d'un audit de provenance/recompilation et d'une exécution hors réseau amont.
- Les tests « blackout Nuvio » sont désormais déclenchés sur les PR pour vérifier les sources/compilations avec domaines bloqués ; **ce n'est pas une inspection complète des requêtes HTTP au runtime réel**.
- TMDB, MDBList, Trakt, YouTube, addons tiers, débrideurs et services de traduction sont **des fournisseurs externes distincts de Nuvio**. Si leur API tombe, les fonctions qui les utilisent peuvent échouer même si Nuvio disparaît sans conséquence.

Les crédits/licences obligatoires ne doivent pas être supprimés : ils ne constituent pas une dépendance de fonctionnement.

## 5. Audit élargi des tests applicatifs

Lancement de la suite Android mobile complète (run [39294007737](https://github.com/anderson76389/Kovelyx/actions/runs/39294007737)) : **1 663 tests exécutés, 5 initialement en échec**.

- Un test d'affichage des notes et un test de migration d'ancienne progression utilisaient un clip de **1 seconde**, alors que l'application applique volontairement une protection contre les vidéos courtes (moins de 121 s), souvent des erreurs de source. Données de test remplacées par de véritables durées d'épisode, sans supprimer cette protection.
- Trois tests de mise à jour Android utilisaient le stockage historique `nuvio_updater` au lieu du vrai stockage applicatif `kovelyx_updater` ; leurs préparations ont été corrigées.
- Les modifications correspondent aux fichiers de test, et **ne suffisent pas à garantir les fonctions tant que leur nouvelle suite n'est pas verte**. Les builds APK et les tests des fonctions sont des vérifications distinctes.
- Contrôle sans serveurs Nuvio : le contrôle statique des sources actives est passé ; la compilation hors Nuvio pour chaque plateforme est lancée séparément. Ce n'est pas une preuve du streaming réel des addons.

## 5. Ce que les tests automatiques n'ont pas prouvé

- Ouvrir réellement les trois applications sur les appareils de l'utilisateur, naviguer sur **chaque écran**, essayer **chaque bouton**, tous les catalogues, liens, addons et débrideurs.
- Authentifier le même vrai compte sur trois appareils, éteindre/redémarrer puis vérifier tous les conflits et reprises de synchronisation.
- Lire chaque GIF, WebP animé ou codec MP4/MOV/WebM sur tous les appareils ; R2 privé est volontairement non configuré par défaut.
- Importer un historique ou une bibliothèque ancienne avec sauvegarde/export et garantie de non-régression.
- Vérifier à l'exécution les connexions DNS, HTTPS/TLS et de toutes les dépendances natives sous blocage de **tous** les hôtes de Nuvio.
- Prouver qu'une version **publique** publiée dans Releases contient bien tous les commits de cette PR non fusionnée.

## 6. Priorités avant fusion et avant une Release présentée comme indépendante

1. **P0** : rendre verts les tests complets en CI et le test réel local Better Auth/D1. Ne pas fusionner une erreur de compilation, de base ou d'authentification.
2. **P0** : terminer la synchronisation des données essentielles : TV, progression de lecture, vues, paramètres, addons, gestion des conflits et des profils, sécurité des comptes.
3. **P0** : persister et protéger les sessions par coffre d'identifiants chiffré sur chaque OS. Tester connexion/déconnexion/reconnexion et séparation des utilisateurs.
4. **P1** : décider si R2 peut être activé en connaissance des quotas et coûts. Si non, garder uniquement le média local et **annoncer** l'absence de synchronisation TV.
5. **P1** : refaire des captures et contrôles runtime sur PC, mobile et TV dans une situation où Nuvio est inaccessible, les fournisseurs tiers restant autorisés.
6. **P1** : confirmer les tests d'installation, signature, migration sans perte, redémarrage, mises à jour et lecture de vidéos.
7. **P2** : isoler puis retirer les anciens modules Supabase/attributions d'UI obsolètes **sans détruire les licences et les clés de stockage locales**.

**Verdict à cette date : compilabilité des trois clients attestée sur le commit précisé ; Cloudflare accessible publiquement ; comptes et sync partiellement intégrés ; indépendance opérationnelle totale non démontrée.** Le projet continue sur PR #40 en brouillon, sans déploiement ni changement de données de production.
