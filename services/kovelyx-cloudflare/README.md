# Kovelyx Cloud — comptes et synchronisation Cloudflare

**Objectif :** ne plus utiliser les serveurs de comptes Nuvio ni un hébergement Supabase. Ce dossier contient un Worker Cloudflare, une base D1, les tables Better Auth et des endpoints de profils et synchronisation.

**État réel : socle serveur implémenté, non déployé sur votre compte Cloudflare et non validé en conditions réelles.** Les nouvelles applis disposent de premiers adaptateurs pour le formulaire e-mail. Les écrans, caches, profils, lecteurs et anciennes RPC Supabase **ne sont pas encore tous migrés**. Le mode invité reste disponible. Les sessions Cloudflare des premières versions sont en mémoire seulement : il faut se reconnecter après avoir fermé l'application. Les flux QR/code TV et la récupération de mot de passe ne sont pas prêts.

## Un hébergement gratuit sans pause 7 jours ?

Oui, Cloudflare Workers Free + D1 n'a pas la règle Supabase de suspension du projet après 7 jours d'inactivité. Mais **gratuit pour toujours n'est pas garanti** et le forfait a des limites :

- **Workers Free** : jusqu'à 100 000 requêtes/jour, 10 ms de CPU par requête (attention aux opérations de hachage des mots de passe).
- **D1 Free** : 5 Go au total, 500 Mo maximum par base, 5 millions de lignes lues/jour, 100 000 lignes écrites/jour.
- Lorsque les quotas gratuits sont dépassés, le service peut limiter/arrêter les requêtes jusqu'à la réinitialisation. **Le hachage Better Auth sur Workers Free doit être mesuré sur un vrai déploiement** : le code non testé peut dépasser 10 ms CPU. Si oui, ne passez pas au forfait payant sans en discuter.
- Sans SMTP tiers, le prototype ne propose pas de confirmation de mail ni de réinitialisation de mot de passe. **Ne pas ouvrir publiquement les inscriptions.**

Sources : [Workers limites](https://developers.cloudflare.com/workers/platform/limits/), [tarifs D1](https://developers.cloudflare.com/d1/platform/pricing/).

## Installation simplifiée — après autorisation Cloudflare

**Si `npx wrangler whoami` fonctionne déjà dans Codespaces**, il n'est pas nécessaire de se reconnecter. Ouvrir le terminal du Codespace et coller **une seule commande** :

```bash
cd /workspaces/Kovelyx && git pull --ff-only && cd services/kovelyx-cloudflare && npm install && node scripts/setup.mjs
```

Le script [`scripts/setup.mjs`](./scripts/setup.mjs) contrôle votre connexion, crée `kovelyx-accounts` dans l'Union européenne si la base n'existe pas, écrit son identifiant public dans `wrangler.toml`, applique la migration D1, déploie le Worker et crée un secret Better Auth **seulement s'il manque**. Il n'affiche pas le secret et ne demande pas de mot de passe utilisateur. **Les inscriptions restent fermées** après le déploiement.

Une fois terminé, copiez l'URL HTTPS `https://kovelyx-cloud....workers.dev` donnée par Wrangler et testez-la en ajoutant `/health`. Si une étape échoue, relevez le **message d'erreur sans aucune clé ou mot de passe** puis reprenez le script après correction : il réutilise la base et le secret déjà présents.

**Important :** l'identifiant public D1 est inscrit dans le `wrangler.toml` de votre Codespace, pas automatiquement envoyé dans GitHub. Pour retrouver ce réglage après suppression du Codespace, sauvegardez l'ID D1 ou validez seulement ce changement dans votre dépôt après contrôle. Ne commitez jamais les fichiers contenant des secrets.

Les sections ci-dessous constituent la **méthode détaillée alternative**, pas des étapes supplémentaires obligatoires si le script a réussi.

## Installation complète gratuite via GitHub Codespaces

**Prérequis :** votre compte Cloudflare, accès propriétaire au repo Kovelyx, et Node.js 22 minimum dans GitHub Codespaces. Ne jamais copier une clé ou un mot de passe dans GitHub ou la conversation.

### 1. Ouvrir Codespaces

Depuis [GitHub Kovelyx](https://github.com/anderson76389/Kovelyx), cliquez **Code → Codespaces → votre Codespace** puis, dans son terminal :

```bash
cd /workspaces/Kovelyx
git pull --ff-only
cd services/kovelyx-cloudflare
npm install
npx wrangler login --device --browser=false
```

Wrangler affiche un lien et un code d'autorisation dans le terminal. Ouvrez ce lien dans votre navigateur et validez le code. Cette méthode officielle de connexion **ne requiert pas `localhost:8976`** et fonctionne dans GitHub Codespaces. Ensuite, vérifiez la connexion avec `npx wrangler whoami`. **Aucun abonnement payant n'est nécessaire pour créer un Worker Free + D1.**

### 2. Créer votre base D1

```bash
npx wrangler d1 create kovelyx-accounts --location weur
```

La commande renvoie un **database_id** (UUID). Dans le panneau des fichiers Codespaces, ouvrez `services/kovelyx-cloudflare/wrangler.toml` et remplacez uniquement :

`database_id = "REPLACE_WITH_YOUR_D1_DATABASE_ID"`

par le vrai ID affiché par Cloudflare. Le nom de la base est déjà `kovelyx-accounts` et la liaison attendue est `DB`. L'identifiant D1 ne donne pas l'accès administrateur aux données, mais ne collez jamais de jetons API ou de secrets.

### 3. Installer les tables

```bash
npx wrangler d1 migrations apply DB --remote
```

Confirmez. La migration `migrations/0001_accounts_and_sync.sql` crée les tables `user`, `session`, `account`, `verification`, `rateLimit`, `kv_profiles` et `kv_sync`.

**Ne jamais** appliquer cette migration à une base déjà peuplée sans l'avoir sauvegardée et testée. Après changement de version de Better Auth, les migrations doivent être revérifiées contre la version installée.

### 4. Configurer la sécurité de l'API

```bash
openssl rand -hex 32 | npx wrangler secret put BETTER_AUTH_SECRET
```

Cette commande crée automatiquement un secret aléatoire fort et le stocke chez Cloudflare ; aucun mot de passe n'apparaît à l'écran. Conservez votre accès Cloudflare : sans ce secret, le Worker refusera d'activer les comptes. Ne créez **aucune** clé `service_role` (c'est propre à l'ancien Supabase).

### 5. Publier et tester

```bash
npx wrangler deploy
```

Wrangler affiche une URL HTTPS du type `https://kovelyx-cloud.VOTRE-SOUS-DOMAINE.workers.dev`. Vérifiez cette URL dans le navigateur en ajoutant `/health`. Vous devez voir une réponse JSON comportant `"status":"ok"`. **Ce test prouve que le Worker répond, pas encore que les comptes fonctionnent.**

Attention : le plafond **10 ms CPU sur Workers Free** peut empêcher Better Auth de gérer les mots de passe. Vérifiez les journaux Cloudflare, la connexion et les quotas avant diffusion.

### 6. Ajouter une seule variable sur GitHub

Ouvrez [Settings → Secrets and variables → Actions → Variables](https://github.com/anderson76389/Kovelyx/settings/variables/actions) puis **New repository variable** :

- **Name** : `KOVELYX_CLOUDFLARE_URL`
- **Value** : l'URL HTTPS du Worker, sans `/health`, par exemple `https://kovelyx-cloud.moncompte.workers.dev`.

Ce n'est **pas un secret** : il s'agit de l'URL publique du serveur.

Les deux anciens secrets `KOVELYX_CLOUD_URL` et `KOVELYX_CLOUD_PUBLISHABLE_KEY` du tutoriel Supabase ne sont **plus utilisés par les nouveaux workflows**. Vous pouvez les supprimer de GitHub Actions lorsqu'aucun autre workflow ne les utilise. Ne les confondez surtout pas avec le secret `KOVELYX_ANDROID_SIGNING_BUNDLE` : **gardez votre signature APK**.

### 7. Recompiler les applications

Après avoir enregistré la variable, il faut publier **de nouveaux fichiers** :

- [Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) : Run workflow si disponible.
- [Android mobile + Android TV signés](https://github.com/anderson76389/Kovelyx/actions/workflows/publish-signed-android.yml) : Run workflow → `all` (nécessite votre secret JKS déjà créé).
- Les anciennes APK Debug ne sont pas forcément installables au-dessus des nouvelles APK signées ; sauvegardez les données et vérifiez signature, `versionCode` et `applicationId`.

### 8. Créer le premier compte, puis fermer les inscriptions

Les inscriptions sont **fermées par défaut** pour protéger votre base contre les inscriptions abusives.

Pour créer un compte de test, dans le terminal :

```bash
printf 'true' | npx wrangler secret put KOVELYX_REGISTRATION_OPEN
```

Dans une application **réellement recompilée** avec votre Worker, utilisez **Créer un compte**, saisissez un e-mail et un mot de passe fort d'au moins 12 caractères. Connectez-vous ensuite sur un second appareil avec le même compte. Si cela échoue, contrôlez Cloudflare Workers → votre Worker → Observability/Logs et n'ouvrez pas les inscriptions au public.

Immédiatement après la création de vos comptes de test :

```bash
printf 'false' | npx wrangler secret put KOVELYX_REGISTRATION_OPEN
```

Ne laissez pas les inscriptions publiques ouvertes sans anti-bot (Cloudflare Turnstile), confirmation e-mail et politique de confidentialité adaptée.

### 9. Faire ses sauvegardes

Les données **restent chez Cloudflare, pas sur GitHub Pages**. Exportez régulièrement un dump privé de D1 avec Wrangler ou une méthode prise en charge par Cloudflare, stockez-le hors de GitHub, puis testez sa restauration. Les comptes **ne sont pas garantis à vie sans sauvegardes** ni contrôle de l'hébergement.

## Architecture de la première version

| Route | Méthode | Protection | État |
|---|---|---|---|
| `/health` | GET | Publique | Implémentée |
| `/api/auth/sign-up/email` | POST | Inscriptions fermées par défaut | Implémentée via Better Auth |
| `/api/auth/sign-in/email` | POST | Rate limiting Better Auth / D1 | Implémentée |
| `/api/auth/get-session` | GET | Session Better Auth | Implémentée |
| `/api/auth/sign-out` | POST | Session Better Auth | Implémentée |
| `/v1/me` | GET | Jeton Bearer valide | Implémentée |
| `/v1/profiles` | GET/POST | Jeton Bearer, propriétaire vérifié | Implémentée |
| `/v1/sync` | GET/POST | Jeton Bearer, propriétaire du profil, révisions | Implémentée |
| Avatar/bannière R2 | — | — | Pas encore implémenté |
| Connexion TV QR/code | — | — | Pas encore implémentée |
| Réinitialisation e-mail | — | SMTP requis | Pas encore implémentée |
| Sync auto des écrans dans les 3 apps | — | — | À migrer : les routes seules ne suffisent pas |
| Suppression définitive de compte | — | — | Pas encore implémentée ; ne pas exposer un faux bouton |

## Sécurité et données personnelles

L'authentification s'appuie sur [Better Auth](https://better-auth.com/docs/) et ses schémas D1. Les données de synchronisation sont **scopées par ID du compte et du profil dans chaque requête**, avec une clé étrangère composite et une limite de taille. Une révision est exigée pour les modifications concurrentes et retourne HTTP 409 en cas de conflit. **La base D1 n'a pas le RLS de PostgreSQL/Supabase** : l'isolation est implémentée par les requêtes du Worker, à tester en live avec deux comptes avant ouverture.

Ne pas synchroniser mots de passe, clés API de débrideurs, secrets OAuth, tokens d'accès ou fichiers personnels bruts. Les profils et données historiques de Nuvio et Supabase ne sont pas automatiquement transférés.

**État d'avancement :** le serveur et les premiers clients de connexion sont versionnés dans le dépôt. Pas de garantie de synchronisation intégrale, de session conservée après redémarrage, de déploiement Cloudflare effectif, de sécurité production ni de compatibilité sur tous les appareils avant tests.
