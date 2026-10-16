# Kovelyx — audit croisé Windows, Android mobile et Android TV

**Contrôle du 17 octobre 2026.** Périmètre : indépendance vis-à-vis des services et dépôts **NuvioMedia** à l'exception temporaire des **comptes utilisateurs historiques**, qui seront remplacés ultérieurement. Ne pas confondre cela avec le fonctionnement sans Internet ni avec l'indépendance de fournisseurs légitimes (TMDB, Trakt, MDBList, addons, GitHub Releases, etc.).

## Conclusion contrôlée

**STATUT : PARTIEL, non certifié 100 %.**

| Plateforme | Sources et installation conservées dans Kovelyx | Exécution sans domaine Nuvio connu | Points bloquants |
|---|---|---|---|
| Windows x64 | EXE, MSI, portable et code dans Kovelyx | Portable lancé 25 s sous blocage des domaines historiques : [preuve](https://github.com/anderson76389/Kovelyx/actions/runs/38918493653) | DLL native P2P/lecteur intégralement reconstructible non prouvée ; essais de lecture et d'extensions non exhaustifs |
| Android mobile | Sources, AAR locaux et APK sur Releases Kovelyx | APK lancé, navigation basique sans DNS Nuvio : [preuve](https://github.com/anderson76389/Kovelyx/actions/runs/38922387126) | AAR Android toutes ABI reconstruit non validé ; signature et mise à jour du canal preview non garanties |
| Android TV | Sources, AAR locaux et APK TV sur Releases Kovelyx | APK TV lancé, navigation télécommande sans DNS Nuvio : [même preuve](https://github.com/anderson76389/Kovelyx/actions/runs/38922387126) | Idem AAR natif ; essais réels de vidéo/stream, TV Box, mise à jour à compléter |

L'audit des domaines historiques en clair dans les DLL, JAR et AAR stockés a réussi : [38917898204](https://github.com/anderson76389/Kovelyx/actions/runs/38917898204). Les scans du code et des dépendances ont réussi : [38924894115](https://github.com/anderson76389/Kovelyx/actions/runs/38924894115). Ces contrôles **ne détectent pas** une URL construite dynamiquement, un domaine tiers contrôlé par une autre société, ni toutes les requêtes réseau possibles pendant une lecture.

## Dépendances résiduelles et qualification

1. **Backend Supabase et synchronisation : à isoler.** `apps/mobile/composeApp/build.gradle.kts`, `apps/desktop/composeApp/build.gradle.kts` et `apps/tv/app/build.gradle.kts` savent injecter `NUVIO_SUPABASE_URL`, `NUVIO_SUPABASE_ANON_KEY` et `NUVIO_SUPABASE_FALLBACK_URL` depuis une configuration de compilation privée. Les valeurs par défaut du dépôt sont vides ; **cela n'établit ni leur présence ni leur absence dans chaque ancien binaire publié**. Si ces valeurs désignent un backend contrôlé par Nuvio, la connexion est bien dans l'exception prévue, **mais pas nécessairement les autres appels** : synchronisation de collections/bibliothèque/progression, catalogues de fonds de profil membres, contenu membres et stockage distant utilisent aussi `SupabaseProvider` ou son équivalent TV. Sans serveur privé Kovelyx fonctionnel, ces fonctions ne peuvent être promises indépendantes. Les fonctions purement locales, les addons et TMDB n'ont pas pour principe de passer par Supabase dans les chemins inspectés.
2. **Moteur natif Windows : blocage reproductible de compilation.** [Reconstruction 38925824205](https://github.com/anderson76389/Kovelyx/actions/runs/38925824205) échoue car le patch `libtorrent-2.0.12-nuvio-windows-system-trust.patch` ne s'applique pas au code extrait après les autres patches. Il ne serait pas sûr de supprimer sans analyse ce correctif relatif aux autorités TLS Windows. La DLL sauvegardée peut démarrer sans Nuvio, mais la reproduction complète de sa provenance n'est pas prouvée.
3. **Moteur Android quatre ABI : compilation native partielle puis échec Gradle.** La même exécution a compilé des portions C++ et échoué à `:engine:compileReleaseKotlin` par conflit JVM Java 17 / Kotlin 21. Le fichier `vendor/engine/platform/android/engine/build.gradle.kts` a été corrigé pour aligner Kotlin sur Java 17. **La réussite d'une reconstruction complète après cette correction n'est pas encore constatée dans GitHub Actions**. Les AAR historiques sont conservés localement pour ne pas casser les applications.
4. **Releases Android : source Kovelyx, canal Beta incomplet.** Les deux sélecteurs `ReleaseSelector.kt` ignorent les tags `android-preview-<sha>` et `tv-preview-<sha>` car ils exigent un numéro de version sémantique. Les updater pointent vers Kovelyx (pas vers l'organisation Nuvio), mais le système de mise à jour par-dessus les premières préversions Debug n'est pas fiable tant que ce flux n'a pas été normalisé et signé avec une clé stable.
5. **Textes et archives : traces sans appel réseau systématique.** Des ressources localisées contiennent encore la phrase `api.nuvio.tv est le serveur officiel` dans des écrans de sélection de serveur/comptes. Certaines archives de distribution iOS non utilisées par les trois canaux actuels pointent toujours vers des téléchargements de l'ancien projet. Il faut les archiver ou nettoyer séparément avant de les distribuer, sans toucher aux crédits GPL obligatoires ni aux schémas de liens profonds de compatibilité.
6. **Espaces de noms historiques : pas une connexion réseau.** `com.nuvio.*`, `nuvio_engine.dll`, `lib-nuvio-engine-android-0.1.4.aar`, les noms de variables CMake/Gradle internes et les notices de licences sont des identifiants techniques ou une traçabilité légale. Les renommer massivement pourrait casser ABI, modules, cache et stockage utilisateur ; cela ne règle aucune dépendance serveur.

## Validation qui manque avant « totalement indépendant, hors comptes »

- Examiner la **configuration intégrée aux véritables EXE et APK distribués**, pour prouver que la clé et le serveur Supabase éventuellement présents ne sont employés que pour les comptes, ou migrer toutes les fonctionnalités de synchronisation/membership vers un backend Kovelyx.
- Reconstruire les DLL/JAR/AAR natives et vérifier SHA, ABI et comportement du P2P, du lecteur MPV et des sous-titres, sans dépôt Nuvio.
- Tester sur matériels réels les parcours catalogue, source/addon configuré, lecture vidéo, sous-titres, reprise, profils locaux, bibliothèque, AllDebrid/Real-Debrid/Trakt/MDBList/TMDB sans compte Nuvio, en journalisant les hôtes atteints.
- Corriger et tester les mises à jour par plateforme avec une **signature Android permanente** ; éviter de confondre signature Debug avec signature de distribution.
- Conserver un backup des sources, de toutes les bibliothèques natives, des APK et de l'EXE **hors de GitHub**.

Ce document n'est **pas** une attestation de fonctionnement de toutes les fonctions. Il distingue les échecs de banc de test des dépendances avérées, les valeurs de configuration optionnelles des serveurs effectivement contactés, et les exceptions de compte des autres services.

Voir également [dossier de preuves](./PREUVE_INDEPENDANCE.md), [audit général](./AUDIT_NUVIO.md), [versions publiées](./RELEASES.md).
