# Reprise autonome de Kovelyx — backend portable hors Cloudflare

Le répertoire services/kovelyx-cloudflare/portable contient une version Node.js
du backend qui réutilise **les mêmes routes** et contrats que le Worker actuel.
Le stockage devient SQLite local et Better Auth reste chargé de l'authentification.
Aucune modification n'est imposée au Worker ou aux lecteurs des trois applications.

## Préparer une base indépendante

Prérequis : Python 3.11+, Node.js 22.13+ et ses dépendances installées par
npm ci depuis services/kovelyx-cloudflare.

Pour une nouvelle installation, depuis services/kovelyx-cloudflare :

    python3 portable/prepare.py --empty --output /dossier-prive/kovelyx.sqlite

Pour restaurer **ses vrais comptes** :
1. Télécharger une archive D1 chiffrée vérifiée et la tester hors ligne avec
   scripts/verify-private-backup.py et la clé privée de récupération.
2. Déchiffrer le SQL sur son ordinateur uniquement dans un répertoire privé
   temporaire, sans l'envoyer à GitHub ni le publier.
3. Depuis services/kovelyx-cloudflare :

    python3 portable/prepare.py --from-sql /chemin-prive/d1.sql --output /dossier-prive/kovelyx.sqlite

**La cible doit être inexistante**. L'outil ne remplace jamais une base
existante et ne se connecte pas à D1. Il contrôle l'export SQL en mémoire,
la cohérence des données, les empreintes médias/catalogues, les clés étrangères
et les comptes de lignes avant de rendre le fichier utilisable. Effacer ensuite
le SQL en clair temporaire.

## Préserver le secret d'origine

L'ancien secret BETTER_AUTH_SECRET doit être conservé **séparément** dans
un coffre-fort privé. Le mettre dans un fichier en lecture propriétaire
uniquement (chmod 600 sur Linux), jamais dans GitHub, ChatGPT ou les logs.
Sans le même secret, certaines sessions et PIN des profils peuvent devenir
invalides : ne pas annoncer une récupération identique.

Démarrage depuis services/kovelyx-cloudflare :

    export KOVELYX_DB_FILE=/dossier-prive/kovelyx.sqlite
    export KOVELYX_AUTH_SECRET_FILE=/dossier-prive/secret.txt
    node portable/server.mjs

Le serveur écoute par défaut **127.0.0.1:8788 seulement**.
Vérifier /ready, puis tester une connexion réelle, les profils, la
bibliothèque, les médias, les PIN et la synchronisation de deux sessions.

## Accès des trois applications

L'accès réseau nécessite un **reverse proxy HTTPS personnel**, un nom de
domaine, un certificat valide, des sauvegardes et un pare-feu. Ne jamais
exposer directement le port Node sur Internet.

    export KOVELYX_PUBLIC_URL=https://comptes.example.org
    export KOVELYX_BIND_HOST=0.0.0.0
    node portable/server.mjs

Le domaine ci-dessus est un exemple et n'est pas un serveur de secours réel.
Les inscriptions sont **fermées par défaut**, même en mode portable.
Ne les ouvrir volontairement que pendant une procédure d'inscription protégée.

Les clients Windows, Android mobile et TV utilisent **KOVELYX_CLOUD_URL au
moment du build** : reconfigurer cette adresse HTTPS dans leur configuration
de construction privée, puis compiler et signer les trois nouveaux packages.
Les APK déjà installés **ne basculent pas automatiquement**. Conserver
identifiant de paquet et clé de signature pour éviter une réinstallation
destructrice, puis tester le flux de mise à jour avant toute bascule.

## Limites et sauvegardes à vie

- Répéter régulièrement une restauration privée dans une nouvelle base.
- Sauvegarder les bases et secrets **en deux lieux hors GitHub** ; protéger
  les clés de récupération et de signature séparément.
- Conserver copies des sources, licences, binaires natifs, outils de build,
  APK/EXE et dernières versions vérifiées.
- Contrôler régulièrement certificats, disque, performance et sécurité de
  Node et Better Auth.
- Garder Cloudflare en production tant qu'une migration réelle et des tests
  sur HONOR Magic V6, Android TV et Windows ne sont pas validés.

Les tests .github/workflows/portable-backend.yml utilisent des comptes
jetables et aucune clé réelle. Cela réduit la dépendance technique à
Cloudflare, sans garantir la disponibilité éternelle de son propre hébergeur,
des catalogues externes ou des canaux de téléchargement.
