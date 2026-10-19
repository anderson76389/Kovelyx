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
- Android TV : consultation des noms de profils Cloudflare déjà existante, sans import automatique dans les profils TV. Les actions de liaison par code historiques sont bloquées sur Cloudflare tant qu'il n'existe pas de nouveau protocole.

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
