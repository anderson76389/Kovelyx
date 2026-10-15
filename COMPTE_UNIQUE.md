# Kovelyx Cloud : compte unique Windows, Android, Android TV, Linux et iOS

État au **15 octobre 2026** : **architecture et première migration SQL déposées**, mais **aucun serveur Kovelyx Cloud n'est encore déployé**, aucun compte Kovelyx indépendant n'a encore été créé, et la synchronisation multiplateforme n'est **pas** opérationnelle. Ne pas demander aux utilisateurs de créer un compte avant déploiement complet.

## Objectif

Une même adresse e-mail et un même compte sur les cinq plateformes. La connexion reste **facultative** : le mode invité et les catalogues locaux continuent de fonctionner même si le cloud tombe. Les données de chaque profil sont isolées par compte.

| Données | Synchronisation prévue | Remarques |
|---|---|---|
| Identité et sessions | Oui | Inscription e-mail, confirmation, récupération du mot de passe, déconnexion des appareils |
| Profils, noms et profil actif | Oui | Identifiants UUID stables entre appareils |
| Avatars et fonds personnalisés | Oui, **après ajout du stockage privé** | Les URI locaux Windows et Android ne sont pas transportables tels quels |
| Favoris, bibliothèque et suivi de lecture | Oui | Versions/révisions et résolution des conflits |
| Catalogues, extensions et ordre | Oui | Références par profil ; ne pas mélanger différents profils |
| Thèmes, réglages et affichage | Oui, si compatibles | Paramètres propres à un écran conservés localement |
| Identifiants Trakt, MDBList, debrid, clés API | **Non par défaut** | Les secrets ne doivent jamais être stockés dans le JSON de sync en clair ; prévoir un coffre chiffré de bout en bout distinct |
| Fichiers vidéo, torrents, téléchargements | Non | Données trop volumineuses ou sensibles, elles restent locales |

La compatibilité multiplateforme ne signifie pas que les cinq applications seront publiées en même temps. Windows et Android sont les distributions actuelles mises en avant, Android TV et iOS sont des clients à intégrer et Linux est pour le moment en pause.

## Infrastructure recommandée

**Un backend PostgreSQL + Supabase Auth indépendant sous le contrôle de Kovelyx**, déployable chez un hébergeur géré puis migrable vers un VPS ou une installation Docker autohébergée. L'API unique doit avoir un domaine contrôlé par le propriétaire, par exemple `accounts.<ton-domaine>` (nom à configurer, **pas un serveur déjà existant**).

Pourquoi réutiliser Supabase ? Le code Windows, Android et iOS possède **déjà des clients Supabase Auth et des modules de synchronisation**. Le remplacement de leur backend peut se faire progressivement, sans réécrire toute l'application d'un seul coup. Supabase est aussi autohébergeable et la base PostgreSQL exportable. Attention : les RPC, fonctions Edge, règles RLS et données nécessaires au fonctionnement actuel **ne se recréent pas automatiquement** dans un nouveau projet.

**Aucun compte ne peut être garanti « à vie »** : domaine, serveur, stockage et sauvegardes nécessitent un entretien et peuvent avoir un coût. Une continuité durable exige des exports vérifiés et une méthode documentée pour migrer vers un autre hébergeur.

## Fichiers actuellement prêts

- [Migration SQL sécurisée](../services/kovelyx-cloud/supabase/migrations/202610150001_kovelyx_accounts_and_sync.sql) : comptes (liés à `auth.users`), profils, préférences de compte et éléments de sync par profil.
- Toutes les tables nouvelles ont des **politiques Row Level Security** par utilisateur authentifié.
- Le backend refuse les comptes anonymes pour ces tables et borne la taille d'un élément à 128 Kio de JSON.
- Le mécanisme de révision côté serveur constitue une **base** de détection des conflits ; un algorithme complet de résolution inter-appareils reste à implémenter.
- Les composants actuels continuent de pointer vers leur configuration existante ; le SQL n'a pas été branché sans serveur opérationnel.

## Mise en route réelle : ce qu'il reste à faire

1. **Créer une infrastructure propre** : projet Supabase sous compte propriétaire ou instance autohébergée toujours allumée. Contrôler le domaine, la base, les clés et le service d'envoi des e-mails. Les e-mails de confirmation et de récupération de mot de passe doivent réellement arriver.
2. Appliquer la migration SQL sur une **base neuve de test**. Tester les politiques avec **deux comptes de test distincts**, en vérifiant qu'aucun ne peut consulter ou modifier les données de l'autre.
3. Ajouter la fonction de découverte Kovelyx, les URL publiques HTTPS et les clés **publishable** nécessaires à chaque application. **Ne jamais** intégrer la clé `service_role`, un mot de passe PostgreSQL ou un secret de signature JWT dans un APK, un EXE ou dans GitHub.
4. **Recréer ou adapter les nombreuses RPC de synchronisation déjà utilisées** par les clients (`sync_pull_profiles`, `sync_push_profiles`, `sync_pull_collections`, `sync_push_collections`, réglages de profil, bibliothèque, extensions, historique, etc.). La nouvelle migration ne les fournit pas encore.
5. Connecter Windows et Android d'abord, valider les scénarios multi-profils et hors connexion, puis Android TV, iOS et Linux. Le compte unique doit conserver **le même identifiant d'utilisateur du backend** sur toutes les plateformes.
6. Préparer un **assistant de migration** de compte ancien vers Kovelyx (export explicite, confirmation avant tout écrasement, mapping des profils). Une identité Nuvio **ne peut pas** être transférée avec son mot de passe sans accord et accès aux systèmes de l'ancien service ; il faudra généralement créer le nouveau compte avec une adresse e-mail, puis transférer les données exportables.
7. Ajouter stockage privé des avatars/bannières, quotas, politique de conservation, export ZIP/JSON, suppression de compte, sauvegardes et restauration validée.
8. Publier des clients **signés avec des clés pérennes** et tester la continuité de session lors de leur mise à jour.

## Sauvegardes indispensables

- Sauvegarde de PostgreSQL **chaque jour** sur un stockage externe distinct ; utiliser un dump cohérent de la base complète gérée.
- Sauvegarde des objets (photos, bannières), du schéma SQL, du déploiement, des clés nécessaires à la restauration (stockées **hors du dépôt**).
- **Chiffrement**, rétention et test de restauration sur une autre instance tous les mois.
- Export manuel complet par utilisateur. Sans cet export, un hébergeur qui ferme met en danger les comptes et leurs données.
- Ne pas commiter de dumps contenant des e-mails, adresses IP, clés privées ou tokens.

## Protection et confidentialité

Inscription avec e-mail confirmé, mots de passe gérés par le fournisseur d'authentification (jamais en clair), transport HTTPS, limitation des tentatives, RLS, séparation des profils et accès utilisateur à l'export/suppression.

Ne jamais synchroniser des identifiants Trakt, debrid, MDBList ou des tokens OAuth en clair. Les appareils peuvent garder ces secrets localement, ou un coffre E2E doit être étudié plus tard.

## Ancienne connexion Nuvio

L'ancienne option de connexion Nuvio pourra coexister **temporairement** comme source d'import des données, mais ne doit pas servir de fondation au compte Kovelyx. Si Nuvio disparaît, ton compte Kovelyx et les données déjà migrées doivent rester utilisables.

**État des fonctionnalités aujourd'hui :** le code client dispose déjà de fonctions locales et d'un module de sync, mais la nouvelle base n'a pas d'authentification déployée et n'est pas connectée : **un nouveau compte Kovelyx synchronisé n'est pas encore activable**.
