# Kovelyx Cloudflare — état de la synchronisation

_Mise à jour : 19 octobre 2026. Ce document décrit le code de la branche de migration, pas une fonctionnalité déjà déployée dans les Releases._

## Preuves disponibles

- Le premier compte utilisateur, la session et le profil « Principal » ont été créés et relus sur le Worker réel depuis Codespaces (rapport utilisateur).
- L'inscription publique a été refermée immédiatement après ce test.
- Les applications Windows, Android et TV contiennent un premier client Better Auth/Cloudflare, distinct du protocole Supabase.
- Le Worker possède une base D1 pour les profils et les catégories de synchronisation, avec isolation par utilisateur et révisions.

## Ce que cette étape ajoute

- Windows et Android : lecture et import des **noms/identifiants UUID** de profils du même compte, sans remplacer bibliothèques, historique, images et autres préférences locales.
- Windows et Android : création/renommage de profil côté Cloudflare, et suppression **explicite** d'un profil (qui supprime aussi ses enregistrements kv_sync). Le dernier profil du compte ne peut pas être supprimé.
- Le mode invité reste local et fonctionne sans autorisation du Worker.
- Les fonctions de synchronisation Supabase incompatibles ne doivent pas s'exécuter pour les comptes Cloudflare dans les chemins de profils/PIN/bibliothèque modifiés.
- Android TV : consultation des noms de profils Cloudflare déjà existante, sans import automatique dans les profils TV.

## Limites réelles

La synchronisation des **favoris, bibliothèque, historiques, progression, préférences, extensions, avatars et fonds** n'est **pas encore opérationnelle entre les appareils**. Les routes D1 de synchronisation existent, mais il manque les adaptateurs applicatifs, les révisions et les tests multi-appareils. Aucun transfert automatique depuis d'anciens comptes Nuvio/Supabase. Les sessions restent seulement en mémoire : reconnexion nécessaire après fermeture. Pas de connexion par code TV, de réinitialisation de mot de passe ni de suppression définitive du compte.

### Mise en service après validation de la branche

1. Exécuter les checks GitHub de la PR (tests API, schéma D1, compilation du Worker et, lorsque possible, builds des apps).
2. Déployer la nouvelle version du Worker avec la configuration D1 existante avant de distribuer les nouveaux clients. Ne jamais remplacer l'ID de la base ni les secrets.
3. Compiler puis publier les nouvelles apps, **sans déclarer une synchronisation complète**.
4. Tester avec le même compte : créer et renommer un profil sur Windows, fermer/réouvrir Android, vérifier le même UUID et le même nom, puis vérifier l'absence de perte de données locales. Tester l'échec de suppression du dernier profil.
5. Effectuer une sauvegarde privée de D1 avant tout test de suppression de profil.

La branche ne déploie pas automatiquement le Worker, ne crée pas de compte supplémentaire, ne touche pas au mot de passe ni aux tokens et ne publie aucun exécutable.
