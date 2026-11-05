# Compte Kovelyx — état technique

Kovelyx utilise exclusivement **Cloudflare Workers, Better Auth et D1** pour son nouveau service de comptes. Les fichiers de migration et le code Worker sont dans [services/kovelyx-cloudflare](../services/kovelyx-cloudflare/README.md).

## Parcours prévus dans les trois applications

- Inscription et connexion par e-mail/mot de passe via Better Auth.
- Profils identifiés par UUID Cloudflare et verrouillage PIN.
- Bibliothèques, progression et historique vu par profil.
- Synchronisation des extensions, catalogues, images de profil et préférences compatibles.
- Mode invité et modifications locales préservées en cas d'indisponibilité du cloud.

## Mise en production

La migration D1 et la publication du Worker sont des opérations distinctes de la compilation des applications. Elles nécessitent des secrets côté serveur, une sauvegarde vérifiée et un contrôle `/ready`. La liaison Android TV par QR/code et le parcours de récupération du mot de passe ne sont pas encore disponibles.

Les tests de compilation ne suffisent pas pour promettre une synchronisation parfaite entre Windows, mobile et TV : tester sur appareils réels création/suppression de profils, PIN, ajout/retrait d'œuvres, suppression d'historique, reprise de lecture et changements de compte. Ne jamais supprimer les données locales pour forcer une migration.
