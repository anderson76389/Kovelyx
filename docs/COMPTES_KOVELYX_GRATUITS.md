# Créer des comptes Kovelyx avec Cloudflare

Kovelyx utilise **Cloudflare Workers, D1 et Better Auth** pour les comptes et la synchronisation privée des profils.

La procédure à utiliser est le [guide officiel du service Kovelyx Cloud](../services/kovelyx-cloudflare/README.md). L'ancien tutoriel de fournisseur de comptes n'est plus applicable ; il a été retiré pour ne pas conduire à déployer la mauvaise infrastructure.

## Mise en service

1. Préparer votre compte Cloudflare et un Codespace autorisé à déployer le Worker.
2. Vérifier et conserver une sauvegarde de la base D1 existante **hors du dépôt** avant toute migration.
3. Suivre le script d'installation/migration et son contrôle `/ready` détaillés dans le guide Cloudflare.
4. Configurer la variable publique GitHub Actions `KOVELYX_CLOUDFLARE_URL` avec l'URL HTTPS de votre Worker.
5. Construire et tester les trois applications avec le même compte et des profils distincts.

Les tests GitHub de schéma ne déploient **pas** automatiquement la migration en production. Les données locales d'une ancienne version ne deviennent pas automatiquement des données Cloudflare ; conservez vos installations et sauvegardes durant la transition.

**Limites :** les flux TV par QR/code et la récupération du mot de passe ne sont pas encore disponibles sur ce backend. Une compilation réussie ne prouve pas la synchronisation sur des appareils réels.
