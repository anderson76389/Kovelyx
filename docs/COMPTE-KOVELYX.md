# Compte unique Kovelyx — plan technique

**État : à mettre en service.** Aucun serveur officiel de comptes Kovelyx n'est actuellement configuré dans les exécutables publics. La présence d'un écran « Créer un compte » ne constitue pas un service de comptes fonctionnel.

## Objectif

Une identité **Kovelyx** indépendante des comptes Trakt et MDBList, utilisable sur Windows, Android et Android TV. Un utilisateur peut créer plusieurs profils, retrouver sa bibliothèque et reprendre la lecture d'un appareil à l'autre. Le mode invité reste disponible sans serveur ni inscription.

## Backend indépendant

- Héberger un projet **Supabase open source (Auth + PostgreSQL + Storage)** ou un backend compatible **sous contrôle du projet Kovelyx** sur une infrastructure à disponibilité continue. Il faut une URL publique stable, HTTPS, des sauvegardes, un domaine et une gestion de secrets ; un PC personnel éteint ne convient pas pour des comptes partagés.
- Mettre la configuration publique du backend dans la compilation Windows (URL et clé **publique**, jamais les clés d'administration), puis harmoniser les applications Android et TV. Les clés privées doivent rester côté serveur.
- Publication des informations de confidentialité réelles (hébergement, durée de conservation, droits de suppression) avant ouverture des inscriptions.

## Parcours d'inscription

1. Création avec adresse e-mail, mot de passe et confirmation d'adresse.
2. Connexion sécurisée et récupération du compte par e-mail, avec protection anti-abus, contrôle des tentatives et limitation de débit.
3. Un identifiant de compte permanent ; profils liés à cet identifiant et isolés par des règles d'accès (RLS).
4. Windows : persistance sécurisée de session et fonctionnement hors ligne avec synchronisation différée.
5. Android : reconnexion au même compte, avec les mêmes identifiants de profil et bibliothèque.
6. Android TV : authentification par **code d'appareil à durée limitée** saisi depuis un navigateur, sans taper de longs secrets à la télécommande.

## Données et synchronisation

- Profils, avatars, fonds, catalogue personnalisé, listes, progression et préférences stockés par utilisateur avec **migrations de schéma** et droits d'accès contrôlés.
- Conserver les photos locales de Windows comme données locales tant qu'elles n'ont pas été volontairement importées dans un stockage cloud autorisé. Ne jamais essayer de synchroniser un chemin de fichier Windows vers Android.
- Synchro hors ligne : file de modifications, reprises sur erreur, détection des conflits, pas d'écrasement silencieux.
- Export JSON complet, import, sauvegardes régulières, révocation des sessions et suppression de compte réellement vérifiable.

## Trakt et MDBList restent facultatifs

Un compte **Kovelyx** sert uniquement à l'application. L'utilisateur peut lier Trakt et/ou MDBList **par profil** pour synchroniser l'historique chez ces fournisseurs, puis les délier sans supprimer son compte Kovelyx.

Les flux d'autorisation par navigateur et code d'appareil exigent que l'éditeur enregistre sa propre application chez chaque fournisseur. Le **client_id** public peut être distribué ; un éventuel **client_secret** ne doit pas être confié à un exécutable public si un échange côté serveur est possible. Sans identifiants d'application valides, le bouton « Se connecter » doit afficher la configuration requise, pas une fausse connexion.

## Définition de terminé

- [ ] Backend Kovelyx en ligne et HTTPS vérifié, avec sauvegarde et domaine appartenant au projet.
- [ ] Création, confirmation, connexion, déconnexion, réinitialisation de mot de passe et suppression de compte testées sur Windows.
- [ ] Deux profils créés sous un compte et synchronisés sur deux appareils Windows de test.
- [ ] Migration non destructive des profils locaux existants après accord explicite.
- [ ] Android mobile connecte le même compte et retrouve les bons profils.
- [ ] Android TV associe ce même compte par code à usage limité.
- [ ] Tests de sécurité, de permissions, de restauration de sauvegarde et de synchronisation hors ligne réussis.
- [ ] Connexion Trakt et MDBList avec des applications OAuth enregistrées au nom de Kovelyx.

**Ne jamais annoncer qu'un compte universel existe avant la réussite de ces tests.** Les services de métadonnées et de lecture constituent un autre problème et doivent disposer de diagnostics indépendants.
