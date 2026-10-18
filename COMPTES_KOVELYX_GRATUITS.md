# Tutoriel officiel : comptes Kovelyx gratuits via Cloudflare

Le projet **a abandonné Supabase comme solution cible des nouveaux comptes**. Le tutoriel Supabase ci-dessous est **archivé et obsolète** : **ne suivez pas ses étapes et ne créez plus de secrets Supabase dans GitHub Actions**.

➡️ **[Guide actuel : installer Kovelyx Cloud sur Cloudflare Workers + D1](../services/kovelyx-cloudflare/README.md)**.

La migration des applications n'est pas encore achevée pour toutes les fonctions de synchronisation. Des bibliothèques Supabase héritées restent dans le code tant que leurs remplaçants n'ont pas été testés.

---

<details><summary>Ancien tutoriel Supabase (historique uniquement)</summary>

## Créer ses propres comptes Kovelyx gratuitement (Windows, Android et Android TV)

> **Guide utilisateur — octobre 2026.** Le compte sera dans **votre projet Supabase** et non sur les serveurs Nuvio. Le code des trois clients a déjà des mécanismes d'authentification Supabase, et les workflows Kovelyx peuvent injecter *votre* URL et *votre* clé publique. **Important : les RPC de synchronisation historiques ne sont PAS compatibles automatiquement avec le schéma Kovelyx Cloud.** Créer un projet et se connecter avec un même e-mail ne signifie pas encore que vos profils, collections, historique, médias et débridages sont synchronisés.

## Coût réel et dépendances

**Supabase Free = 0 €/mois** tant que vous restez au forfait Free (sans add-on ni passage vers une offre payante). En octobre 2026 : **2 projets actifs**, **50 000 utilisateurs mensuels actifs**, **500 Mo de Postgres par projet**, **1 Go de stockage de fichiers**, **5 Go d'egress** et restrictions d'usage. Le projet gratuit peut être **mis en pause après ~7 jours de faible activité**. Aucun engagement « gratuit à vie » ni aucune garantie d'hébergement éternel : lisez les conditions et surveillez le tableau d'utilisation.

Sources officielles : [Supabase Free](https://supabase.com/pricing), [facturation](https://supabase.com/docs/guides/platform/billing-on-supabase), [pause des projets](https://supabase.com/docs/guides/platform/free-project-pausing). Supabase est un service tiers ; cette solution est **indépendante de Nuvio**, mais **hébergée par Supabase**. Vous pourrez migrer vers un Supabase autohébergé en conservant vos exports de base, comptes et clés ; cette migration nécessite du travail.

## Étape 1 — Créer le projet Supabase à votre nom

1. Ouvrez [supabase.com/dashboard](https://supabase.com/dashboard), créez un compte Supabase personnel et sélectionnez **Free**.
2. Sélectionnez **New project** dans une organisation que vous administrez.
3. **Name :** `Kovelyx-Cloud` ; **Region :** une région européenne proche ; **Database Password :** un mot de passe fort, gardé dans un gestionnaire de mots de passe.
4. Créez le projet et attendez la fin de l'initialisation.
5. Dans les paramètres du projet ou la fenêtre **Connect**, notez l'URL du projet, au format `https://abcdefgh.supabase.co`. Prenez **Project publishable key** : une valeur commençant par `sb_publishable_`.
6. **Ne prenez jamais** une clé commençant par `sb_secret_`, ni une clé `service_role`. Celles-ci contournent la protection de la base et ne doivent jamais entrer dans des APK/EXE.

La clé publishable est conçue pour les applications distribuées. Sa sécurité dépend des politiques RLS définies en base. Voir [les clés API Supabase](https://supabase.com/docs/guides/getting-started/api-keys).

## Étape 2 — Préparer les comptes

Dans **Authentication → Sign In / Providers → Email**, activez le fournisseur **Email** et les inscriptions si vous souhaitez créer les comptes depuis les applications.

**Test personnel le plus simple, sans service d'e-mails :** dans **Authentication → Users → Add user**, créez vous-même un utilisateur avec **votre adresse e-mail réelle** et un mot de passe robuste, en confirmant cet utilisateur dans l'interface d'administration si cette option est proposée. Ce compte peut ensuite se connecter par e-mail et mot de passe depuis les applications configurées.

**Pour autoriser amis/famille à s'inscrire eux-mêmes :** prévoyez un fournisseur **SMTP externe configuré** dans **Authentication → Emails → SMTP Settings**, car l'expéditeur gratuit par défaut de Supabase **ne peut écrire qu'aux adresses e-mail des membres autorisés de l'organisation** et est limité à environ **2 e-mails par heure**. Pour faire un test de création sans mail, la désactivation temporaire de « Confirm Email » est possible, mais déconseillée pour une application publique (risque d'e-mails non vérifiés, récupération de mot de passe limitée). Pour une vraie application, gardez la confirmation et fournissez votre propre SMTP.

Voir [restrictions e-mail et SMTP](https://supabase.com/docs/guides/auth/auth-smtp) et [configuration Auth](https://supabase.com/docs/guides/auth/general-configuration).

Dans **Authentication → URL Configuration**, utilisez votre site officiel `https://anderson76389.github.io/Kovelyx/` comme *Site URL* si nécessaire pour les confirmations. Ne supposez pas que cette page d'accueil est une interface de connexion ou qu'elle gère les liens de vérification : les callbacks e-mail et les redirections des applications doivent être implémentés/testés avant d'activer les parcours par e-mail en production.

## Étape 3 — Installer votre base Kovelyx (sans Nuvio)

Le projet GitHub possède déjà le SQL de base dans :

[`services/kovelyx-cloud/supabase/migrations/202610150001_kovelyx_accounts_and_sync.sql`](../services/kovelyx-cloud/supabase/migrations/202610150001_kovelyx_accounts_and_sync.sql)

1. Sur GitHub, ouvrez ce fichier et utilisez le bouton **Raw** pour récupérer **le code SQL complet**.
2. Dans Supabase → **SQL Editor → New query**, collez le SQL **uniquement dans votre projet Kovelyx neuf**.
3. Vérifiez que l'éditeur est connecté au **bon projet** et exécutez **Run**. Le code crée trois tables : `kovelyx_account_preferences`, `kovelyx_profiles` et `kovelyx_sync_items`, ainsi que leurs politiques **RLS**.
4. Contrôlez leur présence en exécutant :

```sql
select schemaname, tablename, rowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in (
    'kovelyx_account_preferences', 'kovelyx_profiles', 'kovelyx_sync_items'
  );
```

Attendez-vous à **3 lignes `rowsecurity = true`**. Cela ne suffit pas à certifier la sécurité : avant de synchroniser les clients, il faudra tester avec **deux utilisateurs connectés différents**, vérifier qu'ils ne lisent/modifient pas les données de l'autre, et éviter les requêtes exécutées avec un administrateur qui contourne RLS.

Ce SQL est une **base de départ**, pas la copie d'une base Nuvio. Les appels `get_member_profile_background_catalog`, `delete-account`, les RPC de connexion TV et de nombreuses procédures de synchronisation ne sont **pas** fournis par ce fichier. Ils devront être migrés ou remplacés dans le code Kovelyx avant activation complète.

## Étape 4 — Relier les trois compilations à votre projet (sans terminal PC)

Dans votre dépôt, ouvrez **[Settings → Secrets and variables → Actions](https://github.com/anderson76389/Kovelyx/settings/secrets/actions)**, puis cliquez **New repository secret** pour chaque entrée :

| Name (à copier exactement) | Secret (valeur) |
|---|---|
| `KOVELYX_CLOUD_URL` | URL de votre projet Supabase : `https://<votre-reference>.supabase.co` |
| `KOVELYX_CLOUD_PUBLISHABLE_KEY` | Votre clé **publishable** : `sb_publishable_...` |

Il ne faut **aucun autre secret Supabase** dans les builds publics. Le script [`scripts/configure-cloud-build.py`](../scripts/configure-cloud-build.py), intégré aux workflows, copie ces **deux** valeurs dans des fichiers `local.properties` non versionnés, en utilisant temporairement les **noms internes hérités** `NUVIO_SUPABASE_URL` et `NUVIO_SUPABASE_ANON_KEY`. **Le domaine réel est celui de votre projet Supabase, pas celui de Nuvio.** Ni les mots de passe utilisateur ni la clé admin de Supabase ne sont stockés dans GitHub.

Le script est **facultatif** : en l'absence des deux secrets, les builds continuent en mode invité. Si un seul secret manque, il fait échouer le build plutôt que de produire une application mal configurée.

## Étape 5 — Construire les applications avec votre serveur de comptes

**Attention : ajouter des secrets ne change pas les APK/EXE déjà publiés.** Il faut de **nouvelles compilations** :

- Windows : [workflow Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml) → *Run workflow* (quand il est disponible).
- Android mobile + TV **signés avec votre JKS** : [workflow APK signés](https://github.com/anderson76389/Kovelyx/actions/workflows/publish-signed-android.yml) → *Run workflow* → `all`. Ce workflow exige également le secret `KOVELYX_ANDROID_SIGNING_BUNDLE` que vous avez déjà préparé. Si la signature ou publication échoue, aucun APK de production n'est garanti.
- Android mobile + TV **Debug de test** : [workflow de compilation Android](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml), qui génère les APK temporaires. Ils ne se mettent pas forcément à jour par-dessus les APK signés.

Sur GitHub, inspectez le résultat de l'exécution et les fichiers des **Releases**. Une compilation ou publication échouée ne rend pas les anciennes APK compatibles avec le nouveau serveur.

## Étape 6 — Vérifier son propre compte

1. Installez une **nouvelle version effectivement compilée** avec vos secrets Supabase (pas l'ancienne préversion).
2. Sur **Windows**, ouvrez l'écran de compte et connectez-vous avec l'e-mail/mot de passe du compte créé dans Supabase.
3. Vérifiez **Authentication → Users** : le compte s'y trouve ; il ne doit jamais exister dans un projet administré par Nuvio.
4. Répétez le test sur Android. Sur TV, privilégiez d'abord le login **e-mail/mot de passe**, car les flux **code TV/QR** d'origine demandent des RPC/Edge Functions qui ne sont pas encore déployés dans ce projet.
5. Vérifiez que les connexions n'utilisent que **votre** projet Supabase et les autres services légitimes. Deux appareils utilisant le même compte auront le même identifiant Supabase Auth ; **leur bibliothèque n'est pas encore synchronisée** tant que le module cloud Kovelyx complet ne sera pas relié à ces trois clients.

Un compte Supabase Admin → Users confirme la **création**, pas à lui seul le fonctionnement de tous les écrans des clients.

## Étape 7 — Sauvegarde et gratuité sur la durée

- Supabase Free peut suspendre un projet après environ 7 jours d'activité insuffisante ; consultez les e-mails de notification et **Resume project** dans le dashboard si nécessaire.
- Les sauvegardes automatiques téléchargeables ne sont pas incluses. Préparez un export SQL périodique privé via [Supabase CLI db dump](https://supabase.com/docs/guides/platform/backups), et conservez en sécurité les mots de passe / accès, les fichiers Storage et une copie sur un support séparé de GitHub.
- Vérifiez régulièrement **Organization → Usage & Billing**. Pour rester à 0 €, ne basculez pas en Pro et n'activez pas d'add-ons payants. La présence d'un site GitHub Pages ne fournit pas de base de données d'authentification.
- Testez la récupération de compte, le changement de mot de passe et l'isolation des utilisateurs avant de partager largement Kovelyx.

## Ce qui reste à développer

La connexion à **un seul compte** sur plusieurs appareils peut s'appuyer sur Supabase Auth dès que la compilation est réellement branchée et testée. Les **profils, favoris, historique, progression, addons, catalogues, photos, fonds, suppression de compte et échanges TV** exigent encore une **migration du code existant vers les tables et procédures Kovelyx Cloud**, des politiques de stockage privées et des tests réels. Cette étape ne peut pas être remplacée par une simple clé API ou une requête SQL.

**N'affichez pas « synchronisation totale » dans les applications avant d'avoir validé ces parcours.**


</details>
