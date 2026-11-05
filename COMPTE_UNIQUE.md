# Compte unique Kovelyx — Cloudflare

## Architecture active

Le serveur de comptes est **Kovelyx Cloud** : Cloudflare Workers + D1 + Better Auth. Les trois applications principales (Android mobile, Windows et Android TV) possèdent des chemins de synchronisation Cloudflare dans le code, sans anciens SDK de comptes installés.

- Une identité commune par compte et des UUID de profils distincts.
- Bibliothèques, éléments vus, progression de lecture, extensions, catalogues et préférences : contrats de synchronisation D1 dans les sources.
- Images privées des profils et verrous PIN : schémas D1 dédiés.
- Les contenus téléchargés et secrets tiers ne sont pas des fichiers de synchronisation publique.
- Le mode invité et les données locales doivent rester utilisables sans serveur.

## Ce qui reste à confirmer

Le Worker en production doit être réellement mis à jour et son endpoint `/ready` doit confirmer les migrations requises, notamment les verrous PIN. Les tests automatisés de compilation et de schéma ne remplacent pas les essais de synchronisation entre appareils réels, y compris les conflits et déconnexions. La connexion par QR/code sur TV n'est pas implémentée dans Better Auth.

## Mise en service et sauvegardes

Consulter le [guide Cloudflare](../services/kovelyx-cloudflare/README.md). Sauvegarder D1 avant toute migration ; ne jamais effacer les données utilisateur ou anciennes sauvegardes pour obtenir un test vert. Les comptes historiques d'un autre service ne sont pas importés automatiquement.
