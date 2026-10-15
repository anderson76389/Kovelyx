# Kovelyx Cloud — serveur de comptes propre au projet

**État : socle de base de données créé, aucun serveur déployé, synchronisation des clients NON connectée.**

Ce dossier est le début du serveur pour un **compte Kovelyx unique et facultatif** utilisable, à terme, sur Windows, Android, Android TV, Linux et iOS. Il ne contient aucune donnée personnelle ni clé privée.

## Choix technique

Supabase Auth + PostgreSQL, déployables sur une infrastructure maîtrisée. Les clients existants savent déjà utiliser un serveur Supabase, mais leurs anciennes procédures RPC ne sont **pas** automatiquement présentes dans le nouvel environnement. La prochaine étape est l'adaptateur de synchronisation Kovelyx, pas une simple substitution d'URL.

## Base de données

La migration initiale est dans :

`supabase/migrations/202610150001_kovelyx_accounts_and_sync.sql`

Elle crée :

- `kovelyx_account_preferences`, rattachée au compte `auth.users` ;
- `kovelyx_profiles` (identifiant UUID stable, nom, chemins médias privés) ;
- `kovelyx_sync_items` (données par compte, profil, catégorie, clé et révision) ;
- des politiques **RLS** de propriété pour `authenticated`, aucune permission directe `anon` ;
- des plafonds de taille et une numérotation des révisions en base.

**Ce n'est pas un backend complet.** L'authentification est assurée par Supabase Auth une fois déployé, et les données ne sont synchronisées qu'après implémentation des clients correspondants.

## Déploiement — prérequis pour un administrateur

1. Créer un projet Supabase **possédé par Kovelyx**, ou installer Supabase avec Docker sur un serveur qui reste allumé.
2. Activer une URL HTTPS, la confirmation d'adresse e-mail et les procédures de récupération du mot de passe. Configurer un service SMTP réel et des URLs de redirection contrôlées.
3. Sur un projet **neuf**, appliquer la migration SQL via la CLI ou la console SQL.
4. Vérifier **avec deux comptes distincts** les règles RLS : A voit les profils et données de A, pas ceux de B ; B ne peut jamais écrire dans les données de A.
5. Configurer le stockage privé des avatars et fonds avec des policies basées sur le propriétaire.
6. Ajouter les endpoints de synchronisation et adaptateurs côté applications, en protégeant les conflits d'écriture. Faire d'abord des tests avec des données fictives et les cinq types d'appareils.
7. Automatiser les sauvegardes chiffrées **sur un stockage séparé** et tester régulièrement la restauration.
8. Activer l'inscription réelle **seulement après** la réussite des étapes précédentes.

## Vérification minimale de sécurité

Dans l'éditeur SQL, après application de la migration :

```sql
select schemaname, tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in (
    'kovelyx_account_preferences', 'kovelyx_profiles', 'kovelyx_sync_items'
  );
```

**Résultat attendu :** trois lignes avec `rowsecurity = true`. C'est un prérequis, **pas un test complet** de l'isolement des comptes. Ne jamais tester la sécurité seulement avec le rôle super-admin ou `service_role`, qui peut contourner RLS.

## Secrets et migrations

- La clé **publishable** (publique) peut être utilisée par les clients quand leur serveur est configuré ; la clé **service_role** et le mot de passe PostgreSQL restent **uniquement côté serveur**.
- Pas de clés, de mots de passe ou de dumps utilisateurs dans GitHub.
- Pour une continuité des comptes : sauvegarde de la base (y compris Auth), des images, des secrets nécessaires à une restauration sur un nouveau serveur et du nom de domaine.
- Le chiffrement des tokens et identifiants de fournisseurs doit être conçu **avant** d'autoriser leur synchronisation.

[Plan produit et compatibilité multiplateforme](../../docs/COMPTE_UNIQUE.md) · [Documentation officielle Supabase autohébergé](https://supabase.com/docs/guides/self-hosting)
