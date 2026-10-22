# Audit d'indépendance Kovelyx — Nuvio, comptes et cloud
**Date : 22 octobre 2026 · branche examinée : `main`, release `v0.1.553-alpha`, PR nº 40.**

## Verdict
**Indépendance d'exécution totale : NON CERTIFIÉE.** La plateforme possède des fichiers sources et des binaires archivés ainsi qu'un backend Workers/Better Auth/D1 propre à Kovelyx, mais le déploiement complet, les reconstructions natives, et les trois clients de compte ne sont pas encore validés ensemble. Ne pas confondre présence de code et fonctionnement d'une Release publique.

| Domaine | Faits constatés | Limite |
|---|---|---|
| Branding / mise à jour | APK `com.kovelyx.tv`, mobile `com.kovelyx.mobile`; les updaters Windows, Android et TV interrogent `anderson76389/Kovelyx` | Des packages `com.nuvio.*` et bundles macOS historiques restent dans les sources ; ce sont des identifiants, pas des appels réseau |
| Sources des applications | Copies présentes dans `apps/desktop`, `apps/mobile`, `apps/tv`; pas de pull automatique de la source Nuvio pour compiler les configurations ordinaires | `docs/PROVENANCE.md` signale des gitlinks/LFS historiques incomplets et des bibliothèques natives à revoir |
| Moteur de lecture | `vendor/engine` et les AAR/JAR/DLL compilés sont archivés | Moteurs binaires Nuvio historiques encore embarqués ; reconstruction complète indépendante non confirmée ni intégrée partout |
| Compte | Les clients Cloudflare sont codés avec Better Auth `/v1/me` et session chiffrée sur la PR | La Release v0.1.553-alpha est basée sur `ccc81746e56c...`, pas sur PR nº 40 ; la migration n'est donc pas livrée |
| Bibliothèque / reprises / vus | `/v1/sync` (bibliothèque et reprise) et `/v1/watched` (vues) sont implémentés dans la PR ; la TV et Compose disposent de raccordements expérimentaux | Aucun parcours inter-appareils validé sur le dernier SHA ni test matériel prouvant la convergence |
| Protection contre le vieux cloud | Sur PR nº 40, Windows/Android bloquent `SupabaseProvider` en mode D1 ; TV a des garde-fous supplémentaires | Plusieurs services Supabase demeurent présents pour les installations configurées en mode historique/autre hébergement |
| Backend autonome | Sources Workers et migrations D1 présentes sous `services/kovelyx-cloudflare`; pas besoin du domaine `nuvio.tv` pour le serveur | `wrangler.toml` contient un ID D1 de substitution ; vrai Worker et migrations doivent être contrôlés côté Cloudflare, aucune opération de production effectuée |
| Médias de profil | Route R2 créée | `[[r2_buckets]]` volontairement commenté ; uploads d'avatars/fonds non activés sur le Worker connu |
| Fonctionnement si Cloudflare tombe | Le stockage local invité peut fonctionner | Authentification distante et synchro multisupport dépendent de Cloudflare ; pas d'indépendance du fournisseur Cloudflare ni sauvegarde restaurée prouvée |

## Corrections apportées pendant l'audit, uniquement sur PR nº 40
1. Refus systématique des RPC Supabase historiques quand Cloudflare est activé dans huit services Android TV : `AddonSyncService`, `PluginSyncService`, `CollectionSyncService`, `HomeCatalogSettingsSyncService`, `ProfileSettingsSyncService`, `ProviderCredentialSyncService`, `ProfileSyncService`, `MediaServerSyncService`.
2. `SupabaseModule` TV : lorsque Cloudflare est actif, client historique créé uniquement sur une origine réservée `.invalid` au lieu d'une URL vide ; aucune restauration, sauvegarde ni actualisation automatique de l'ancienne session Supabase. Le backend Kovelyx reste accessible uniquement par son client dédié.
3. `scripts/tests/check-cloud-account-boundaries.py` : nouvelles assertions de présence des garde-fous des huit services et du module Hilt.
4. `scripts/tests/verify-upstream-independence.py` : suppression de l'ancienne autorisation de laisser `https://nuvio.tv/link` dans les sources de production. La seule exception préexistante restante concerne une constante d'identification de serveur qui sert **à rejeter** l'ancien backend, pas à le contacter.

## Preuves et limites
- **GitHub Releases** : la plus récente Release groupée vérifiée pendant cet audit était `v0.1.553-alpha` (21 octobre), produite à partir de `ccc81746e56c...`. Elle contient un EXE, un MSI, une version portable, un APK Android et un APK Android TV, tous expérimentaux et non signés pour production.
- **CI sur ce commit de Release** : compilation APK et packaging Windows réussis ; job « Android mobile - tests JVM de la cible Android » **échoué** dans `Tester les fonctions Kotlin Android TV et Android mobile` (run `39688740345`). L'audit des dépendances (run `39688740337`) était réussi ; il n'établit pas une indépendance de fonctionnement réel.
- **PR nº 40** : branche en brouillon, conflit de fusion (`mergeable: false`). Les tests verts du 21 octobre concernaient un ancien SHA `1b58e7d...`, et non les corrections de ce document. Ils n'attestent ni compilation ni exécution des dernières modifications.
- **Worker réel** : URL publique utilisée par les workflows GitHub `https://kovelyx-cloud.mailpromahe.workers.dev`. Le workflow de vérification teste `/health`, Better Auth et les réponses 401/403, mais ce rapport ne prétend pas avoir testé le contenu privé d'un compte ni exécuté la migration D1 0002.
- **Contrôles statiques existants** : `scripts/tests/verify-upstream-independence.py`, `scripts/tests/audit-tv-nuvio-urls.py` et `scripts/tests/audit-packaged-binaries.py`. Ils ne voient pas toutes les adresses calculées ou chiffrées. Le rapport `docs/AUDIT_DEPENDANCES.md` additionne aussi des identifiants internes `com.nuvio`, d'où un nombre brut qui n'est **pas** un nombre d'appels à des serveurs Nuvio.

## Priorités avant de pouvoir dire « indépendant à 100 % »
1. Résoudre le conflit de la PR nº 40, compiler et exécuter les tests de toutes les plateformes **sur le dernier commit**. Corriger notamment le job Android mobile actuellement en échec sur la Release.
2. Faire un essai noir : couper les DNS/services Nuvio pendant l'ouverture de session Kovelyx, la navigation, l'ajout en bibliothèque, le visionnage, la reprise et la validation « vu » sur Windows, Android et TV.
3. Sauvegarder la base D1, contrôler `0002_watched_items.sql` et déployer le Worker seulement avec autorisation explicite ; tester les comptes, les profils et la restauration de données avec deux utilisateurs de test indépendants.
4. Valider l'archive des bibliothèques natives et une compilation reproductible sans récupération chez `NuvioMedia`. Conserver les licences et mentions légales obligatoires liées au code dérivé.
5. Documenter les fonctions restant locales ou non migrées (PIN, réglages de profils, extensions, catalogues, identifiants de fournisseurs, R2) sans leur promettre une synchronisation qui n'existe pas.

**Aucune donnée de compte, base D1 de production, Worker ou Release n'a été modifiée dans cet audit.** Les corrections sont dans une PR non fusionnée. Ce fichier n'est pas une preuve de tests matériels.
