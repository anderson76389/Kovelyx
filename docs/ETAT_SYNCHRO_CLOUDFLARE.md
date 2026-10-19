# Kovelyx Cloudflare — état de la synchronisation

_Mise à jour : 19 octobre 2026. Ce document décrit le code de la branche de migration, pas une fonctionnalité déjà déployée dans les Releases._

## Preuves disponibles

- Le premier compte utilisateur, la session et le profil « Principal » ont été créés et relus sur le Worker réel depuis Codespaces (rapport utilisateur).
- L'inscription publique a été refermée immédiatement après ce test.
- Les applications Windows, Android et TV contiennent un premier client Better Auth/Cloudflare, distinct du protocole Supabase.
- Le Worker possède une base D1 pour les profils et les catégories de synchronisation, avec isolation par utilisateur et révisions.

## Ce que cette étape ajoute

- Windows et Android : lecture et import des **noms/identifiants UUID** de profils du même compte, sans remplacer bibliothèques, historique, images et autres préférences locales.
- Windows et Android : création/renommage de profil côté Cloudflare, et suppression **explicite** d'un profil (qui supprime aussi ses enregistrements kv_sync). Le profil principal et le dernier profil du compte ne peuvent pas être supprimés.
- Le mode invité reste local et fonctionne sans autorisation du Worker.
- Les fonctions de synchronisation Supabase incompatibles ne doivent pas s'exécuter pour les comptes Cloudflare dans les chemins de profils/PIN/bibliothèque modifiés.
- Windows/Android mobile : première implémentation de la **bibliothèque locale** sur D1 (GET paginé par catégorie et écriture avec contrôle de révision). La première connexion importe les éléments du cloud sans exporter automatiquement les anciennes bibliothèques de l'appareil. Après initialisation, les ajouts et suppressions *effectués dans la bibliothèque locale* peuvent être envoyés au serveur avec refus des conflits (409). Les éléments du cloud sont importés de façon additive; les tombstones ne retirent un titre que s'il était déjà connu sur le même profil.
- Les bibliothèques et caches de profils Windows/Android sont désormais isolés par ID du compte pour éviter de mélanger anciens favoris invités et nouveau compte.
- Android TV : consultation des noms de profils Cloudflare déjà existante, sans import automatique dans les profils TV. Les actions de liaison par code historiques sont bloquées sur Cloudflare tant qu'il n'existe pas de nouveau protocole. Les entrées de synchronisation bibliothèque historiquement reliées à Supabase refusent toute requête en mode Cloudflare, tandis que la bibliothèque locale reste utilisable.

## Présentation des applications sur cette branche

- Les noms affichés par les variantes de développement mobiles et TV sont désormais « **Kovelyx** » (suppression de « Kovelyx (test) » et « Nuvio Debug »). Le package MSI Windows était déjà « Kovelyx ».
- Le logo du chargement Compose est ancré au **centre réel** de son écran sur Windows, mobile et TV ; l'indicateur est affiché en dessous sans déplacer le logo.
- Une vérification statique des labels et positions est ajoutée aux tests de marque, et les APK Android sont contrôlés par `aapt` pour vérifier le **vrai nom installé**.
- Les identifiants `com.kovelyx.*` restent inchangés : pas de réinitialisation d'application ni de confusion avec les données existantes.
- Reste à confirmer le rendu optique sur les appareils réels, notamment les premières images du splash natif Android.

## Limites réelles

La synchronisation de **bibliothèque locale Windows ↔ Android** dispose désormais d'un premier code bidirectionnel mais **n'est pas encore validée par un essai complet multi-appareils**. Elle ne couvre pas les listes externes Trakt/MDBList ni une migration automatique de l'ancienne bibliothèque invitée. La synchronisation des **historiques, progression, préférences, extensions, avatars, fonds** et **la bibliothèque Android TV** n'est **pas encore opérationnelle entre les appareils**. Les routes D1 de synchronisation existent, mais il manque les adaptateurs applicatifs, les révisions et les tests multi-appareils. Aucun transfert automatique depuis d'anciens comptes Nuvio/Supabase. Les sessions restent seulement en mémoire : reconnexion nécessaire après fermeture. Pas de connexion par code TV, de réinitialisation de mot de passe ni de suppression définitive du compte.

### Mise en service après validation de la branche

1. Exécuter les checks GitHub de la PR (tests API, schéma D1, compilation du Worker et, lorsque possible, builds des apps).
2. Déployer la nouvelle version du Worker avec la configuration D1 existante avant de distribuer les nouveaux clients. Ne jamais remplacer l'ID de la base ni les secrets.
3. Compiler puis publier les nouvelles apps, **sans déclarer une synchronisation complète**.
4. Tester avec le même compte : créer et renommer un profil sur Windows, fermer/réouvrir Android, vérifier le même UUID et le même nom. Ajouter ensuite un titre sur Windows et vérifier l'apparition sur Android, puis l'inverse. Vérifier les suppressions et les conflits sans perte de données locales. Tester l'échec de suppression du profil principal.
5. Effectuer une sauvegarde privée de D1 avant tout test de suppression de profil.

La branche ne déploie pas automatiquement le Worker, ne crée pas de compte supplémentaire, ne touche pas au mot de passe ni aux tokens et ne publie aucun exécutable.

## Important : ordre de déploiement

Ce code nécessite que **le nouveau Worker D1 avec pagination de catégorie soit déployé avant les nouveaux APK/EXE**. Les anciens clients ne connaissent pas le nouveau curseur de pagination. Ne jamais lancer les versions de préproduction sur les données définitives sans sauvegarde de D1 et des profils locaux. Aucune Release ne peut être qualifiée de « synchronisation complète » tant que la connexion, la bibliothèque et les redémarrages réels sur deux appareils n'ont pas été testés.


## Focus compte et Android TV — avancée du 19 octobre 2026

- **TV, branche uniquement :** un coffre de session AES-GCM AndroidKeyStore a été ajouté dans `TvCloudSessionVault.kt`. Le mot de passe n'est pas stocké ; le jeton n'est accepté au redémarrage qu'après validation HTTPS `/v1/me`.
- La déconnexion efface la valeur chiffrée enregistrée sur l'appareil. Une panne du Worker empêche la reconnexion automatique sans effacer systématiquement le coffre.
- **État de preuve :** le code est sur la PR, pas dans les APK publics. Le coffre est chiffré, sa restauration vérifie `/v1/me`, et une protection supplémentaire empêche de ressusciter une session effacée pendant la requête. La compilation, le comportement avec l'AndroidKeyStore sur TV Box réelle, la reconnexion et les transferts effectifs restent à confirmer.
- **TV et D1 :** un premier adaptateur de bibliothèque bidirectionnel a été ajouté **sur la branche uniquement** : association prudente UUID/identifiant TV, cache séparé par compte, import distant, mutations CAS et reprise des écritures différées. Les données invitées ne sont pas importées automatiquement. **Compilation finale, conflits et parcours réel multi-appareils non encore certifiés.**
- **Windows et Android :** les premiers adaptateurs de bibliothèque D1 restent expérimentaux ; leur présence dans les sources n'établit pas une synchronisation multi-appareils vérifiée.
- **À ne pas faire :** fusionner/publier ou déployer un nouveau Worker en considérant la synchronisation terminée. Conserver la sauvegarde D1 et les profils locaux. Le service de comptes hébergé n'a pas été modifié durant cette étape.

### Critères d'acceptation prioritaires

1. Valider sur TV réelle deux redémarrages avec jeton valide, puis les cas jeton révoqué, réseau absent, changement de compte et déconnexion pendant une restauration.
2. Vérifier l'isolation du cache D1 TV lors d'un changement de compte, les associations 1:1 et par nom unique, et ajouter un choix explicite pour les cas ambigus sans jamais migrer silencieusement le catalogue invité.
3. Tester le scénario Windows → Android → TV → Windows sur un compte de test, avec les mêmes favoris et progressions, puis un conflit, un mode hors ligne et un second utilisateur distinct.
4. Résoudre les échecs des tests unitaires TV concernant la source HTTP 404, l'allocateur Media3, l'API soutiens non configurée et la configuration Trakt.
5. Publier uniquement un build signé et vérifié après un résultat CI vert, puis tester la mise à jour sans effacement des données.


### Ajout sur la PR nº 40 — bibliothèque Cloudflare Android TV

- `CloudflareTvProfileBinding` associe les profils uniquement avec une relation non ambiguë : un seul profil de chaque côté, ou correspondance unique des noms lorsque plusieurs profils existent. Les homonymes restent non associés et leurs données ne sont pas mélangées.
- `CloudflareTvLibraryStore` utilise une sauvegarde locale **par propriétaire et UUID cloud** distincte de la bibliothèque invitée. L'ancien magasin TV n'est pas effacé ni envoyé automatiquement à D1.
- Le client lit la catégorie D1 `library` par pages avec limites, puis transforme le même format réduit de métadonnées que les applications PC/mobile. Les modifications TV sont sauvegardées localement avant l'envoi et utilisent la révision CAS ; en cas de conflit, elles restent en attente.
- La bibliothèque locale affichée en mode compte est celle de D1 (cache du compte), tandis que le mode invité conserve son stockage historique. Le déclenchement de la lecture est lié à l'état de connexion et au profil actif, pas seulement à l'ouverture des paramètres du compte.
- **Limites majeures :** seuls les favoris de la bibliothèque locale sont concernés ; pas encore de sync TV des progressions, épisodes vus, listes externes, paramètres, addons ou secrets. Pas de transfert de médias tant que R2 est désactivé. Aucun appareil physique ni transfert PC–mobile–TV n'a été testé pendant cette étape. Les profils ambigus exigent encore un futur choix explicite.
- **Production inchangée :** ne pas distribuer cette branche avant une suite CI verte et le déploiement du Worker D1 compatible, après sauvegarde de la base existante.

- **Reconnexion et relance TV :** le dépôt teste la présence des catégories cloud au changement d'utilisateur/profil et périodiquement toutes les cinq minutes tant que l'application TV reste active ; les erreurs réseau sont conservées pour nouvelle tentative. Les annulations de coroutines ne sont pas traitées comme de simples erreurs réseau.
- **Défauts du moteur TV mis en évidence par tests réels du runner :** le test serveur localhost reçoit un code HTTP 400 lorsqu'il envoie 404 ; le test allocateur conserve 196 608 octets après reset/libération tardive. Ces deux défauts de la bibliothèque Media3 locale restent des échecs à corriger dans le moteur précompilé ; les tests n'ont pas été supprimés ni rendus artificiellement verts.


### Sécurisation du lecteur Android TV — moteur Media3 précompilé

Le runner GitHub montre deux défauts **dans les archives AAR vendoriées**, indépendants de Cloudflare : `LocalhostZeroCopyDataSource` interprète une réponse HTTP 404 comme 400, et `DefaultAllocator` conserve 196 608 octets après libération tardive en mode natif.

**Correctif de l'application, distinct d'une recompilation Media3 :** la nouvelle `SafeLoopbackDataSource` route HTTP(S) localhost/127.0.0.1/::1 via OkHttp et préserve les schémas de fichiers standards via `DefaultDataSource`. Les allocations directes natives des chemins de lecture TV, remises en cache et bandes-annonces sont **temporairement désactivées** ; les fonctions réseau et de lecture classiques restent configurables. Le code choisit le mode heap plutôt que le mode natif bogué. Les tests de la nouvelle politique vérifient la sélection et la propagation des erreurs.

**Important :** les deux tests originaux des AAR sont volontairement conservés et peuvent encore échouer, car le contournement ne modifie pas leurs classes internes. Le correctif final des AAR nécessite les sources correspondantes ou une reconstruction traçable de leur fork Media3. Aucune garantie de lecture 100 % sur toutes les box n'est donnée avant tests physiques. Ces mécanismes visent à éviter les deux bogues connus dans les chemins effectivement utilisés par Kovelyx, sans faire croire que les binaires historiques sont réparés.
