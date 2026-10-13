# KOVELYX

**Kovelyx — PC Windows · Android · Android TV**

> **État : quatre snapshots sources intégrés ; compilations de référence en cours de vérification.** Aucun APK ni MSI Kovelyx utilisable n'est encore confirmé. Le changement d'identité, la reprise des dépendances natives et l'autonomie du serveur restent à réaliser.

Kovelyx est un projet communautaire indépendant destiné à préserver et adapter une base d'applications multimédias open source à travers trois plateformes, avec la possibilité de contrôler l'hébergement, les compilations et les mises à jour.

**Ce dépôt n'est pas affilié à NuvioMedia.** Les sources initiales proviennent des dépôts publics Nuvio, dans le respect de leurs licences. Les noms et visuels Nuvio présents dans une copie de travail sont conservés pour la traçabilité en attendant une identité Kovelyx distincte.

## Plateformes

| Plateforme | Emplacement après import | Compilation de référence |
| --- | --- | --- |
| Android TV / Google TV | `apps/tv/` | `./gradlew :app:assembleFullDebug` |
| Android mobile | `apps/mobile/` | `./gradlew :androidApp:assembleFullDebug` |
| Windows | `apps/desktop/` | `gradlew.bat :composeApp:packageReleaseMsi` |
| Serveur / synchronisation | `server/` | Déploiement Docker ; dépendances à auditer |

Les projets sont regroupés dans **un seul dépôt GitHub**, mais chaque application conserve son propre système de compilation Gradle. Le dossier `server/` représente une configuration d'auto-hébergement, **pas** le code source complet de tous les services hébergés.

## Origines et licences

| Projet amont | Référence | Licence |
| --- | --- | --- |
| [NuvioTV](https://github.com/NuvioMedia/NuvioTV) | `dev` | GPL-3.0 |
| [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) | `cmp-rewrite` | GPL-3.0 |
| [NuvioDesktop](https://github.com/NuvioMedia/NuvioDesktop) | `Dev` | GPL-3.0 |
| [Nuvio self-host](https://github.com/NuvioMedia/self-host) | `main` | Apache-2.0 |
| [MPVKit](https://github.com/NuvioMedia/MPVKit) | Commit épinglé | Voir licence du composant |

Les fichiers de licence d'origine sont conservés dans chaque répertoire importé. **Le code dérivé des projets GPL reste sous GPL** lors de sa redistribution. Les marques, logos, services tiers et licences d'autres dépendances doivent être examinés séparément.

Voir [PROVENANCE.md](docs/PROVENANCE.md) et [INDEPENDANCE.md](docs/INDEPENDANCE.md).

## Initialisation automatisée du code source

Les quatre projets sources ne sont **pas** créés à partir de coquilles vides : le workflow [Import des sources](.github/workflows/import-sources.yml) utilise [scripts/import-upstream.sh](scripts/import-upstream.sh) pour récupérer des **commits précis** de projets publics et les importer dans les quatre répertoires.

- Import **à usage unique**, sans écraser les modifications de Kovelyx.
- Provenance et SHA des commits enregistrés.
- Pas de sous-modules Git conservant silencieusement un lien dynamique vers l'amont pour MPVKit : le code de MPVKit est recopié à la version épinglée.
- Les autres Gitlinks, les objets Git LFS et les images Docker restent à inventorier et sécuriser avant toute affirmation d'autonomie totale.

Le workflow d'importation a été **exécuté avec succès** sur les quatre snapshots. Un [workflow de compilation de référence](.github/workflows/build.yml) tente de produire les fichiers APK et MSI ; sa réussite est vérifiable dans l'onglet **Actions** du dépôt. Les exécutables ne sont pas encore certifiés.

## État des compilations

- [Journal de la dernière tentative et des tests](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml).
- Les objets Git LFS, les bibliothèques natives Gitlink manquantes et les images Docker externes empêchent pour l'instant toute promesse d'indépendance complète.
- Le fichier `apps/desktop/.kovelyx-binaries-a-sauvegarder.txt` documente le binaire Windows de 115 Mo manquant dans Git.
- Les décisions et blocages sont suivis dans les [issues](https://github.com/anderson76389/Kovelyx/issues).

## Feuille de route

1. [x] Importer les 4 répertoires sources et enregistrer leurs commits (licence historique MPVKit restant à confirmer).
2. [ ] Traiter les Gitlinks non résolus, les binaires LFS et les images Docker privées/externes.
3. [ ] Obtenir des compilations de référence Android TV, Android mobile et Windows.
4. [ ] Choisir des identifiants Android et des signatures propres à Kovelyx.
5. [ ] Remplacer les références de marque, liens de mise à jour et services Nuvio.
6. [ ] Auto-héberger les fonctions de compte et synchronisation et tester la reprise des données.
7. [ ] Mettre en place des tests et les publications APK/MSI indépendantes.

**Important :** pouvoir cloner le code n'implique pas que les addons, services tiers, métadonnées ou hébergeurs de vidéos continueront toujours à fonctionner.

## Construire localement

Une fois l'import terminé, sous Windows :

```powershell
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx\apps\tv
.\gradlew.bat :app:assembleFullDebug
```

Pour Android mobile, remplace `apps\tv` par `apps\mobile` et la tâche par `:androidApp:assembleFullDebug`. Pour Windows, ouvre `apps\desktop` et lance `.\gradlew.bat :composeApp:packageReleaseMsi`.

Ces commandes sont celles des bases de référence et **ne constituent pas une garantie de compilation**. Elles requièrent JDK/Android SDK et certaines dépendances natives suivant la plateforme.

---

*Projet indépendant. Utiliser uniquement des sources multimédias et addons que l'on est autorisé à utiliser.*
