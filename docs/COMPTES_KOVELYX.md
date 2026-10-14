# Comptes Kovelyx — serveur indépendant

> **État actuel :** Kovelyx peut utiliser un backend Supabase d'authentification **distinct de Nuvio**, mais ce dépôt ne contient pas encore de serveur de comptes public déployé. L'application fonctionne en mode invité tant qu'aucun backend Kovelyx n'est configuré. Ne considérez pas les comptes en ligne comme opérationnels avant les essais réels.

## 1. Créer le service de comptes **Kovelyx**

1. Créer un **nouveau** projet sur [Supabase Dashboard](https://supabase.com/dashboard), sous **votre propre compte de propriétaire**. Ne copiez aucune URL, identifiant ou clé de Nuvio.
2. Dans **Authentication → Providers**, activer **Email** et la confirmation de l'adresse si désirée. Configurer un domaine/SMTP adapté **avant une ouverture à d'autres utilisateurs** ; l'e-mail mutualisé par défaut peut être limité.
3. Dans **Project Settings → API / Connect**, noter uniquement **Project URL** (`https://votre-projet.supabase.co`) et **clé publishable/anon publique**. **Ne jamais insérer une clé `service_role` / `sb_secret_*` dans l'application, les variables publiques ou Git.**
4. Ouvrir ce dépôt GitHub → **Settings → Secrets and variables → Actions → Variables → New repository variable** et créer :
   - `KOVELYX_SUPABASE_URL` = adresse HTTPS de **votre** projet.
   - `KOVELYX_SUPABASE_PUBLISHABLE_KEY` = clé publique publishable/anon de **votre** projet.
5. Déclencher une **nouvelle compilation** depuis Actions/Windows. La configuration est incorporée dans les futurs installateurs ; les anciennes versions déjà installées n'en bénéficient pas. Le paquet GitHub **ne déploie pas** Supabase.
6. Sur un PC de test, vérifier inscription, e-mail de confirmation si activé, déconnexion et reconnexion. Vérifier dans votre **propre** tableau de bord Supabase que le nouveau compte apparaît dans **Authentication → Users** et n'a jamais été envoyé à Nuvio.

**Important :** les variables de type URL/clé publique se retrouvent dans les exécutables distribués, par conception. Elles ne sont **pas des mots de passe serveur**. Les tables, les stratégies RLS, les politiques de sauvegarde, la protection anti-abus, la configuration SMTP et la suppression de compte doivent être mises en place pour ouvrir un vrai service à d'autres personnes.

## 2. Indépendance et synchronisation

Le service d'authentification utilisé est **celui de votre projet**, pas celui de Nuvio. Le code existant sait déjà créer des comptes email/mot de passe avec une API Supabase. Le choix Supabase Cloud dépend cependant de Supabase comme hébergeur. Une option d'indépendance d'hébergement ultérieure est une instance Supabase/GoTrue auto-hébergée, avec VPS, sauvegardes et domaine HTTPS.

**Attention : inscription ≠ synchronisation des bibliothèques.** Plusieurs fonctionnalités du client appellent des fonctions RPC SQL existantes (par exemple `sync_pull_library`, `sync_push_library_items`, `sync_pull_watch_progress`). Un nouveau projet Supabase vierge ne les contient pas. Le service de synchronisation complet nécessitera son schéma SQL, ses migrations et des politiques de sécurité propres avant sa mise en production.

Les comptes Nuvio existants **ne sont pas migrés automatiquement**. Ne saisissez jamais le mot de passe Nuvio dans un formulaire Kovelyx tant que vous n'avez pas configuré et vérifié le nouveau backend.

## 3. En cas de backend absent

L'application indique que les comptes Kovelyx ne sont pas disponibles et permet de rester **invité avec profils et données locales**. Elle ne doit ni présenter une inscription comme réussie, ni envoyer les identifiants à l'ancien serveur Nuvio.

Pour toute ouverture publique à des utilisateurs, finaliser au préalable les [informations comptes et confidentialité](COMPTES_ET_CONFIDENTIALITE.md), la gestion des demandes de suppression et un canal de support privé.
