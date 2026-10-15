# Audit fonctionnel Kovelyx : Windows, Android, Android TV

État du code au **15 octobre 2026**. Ce document distingue les preuves produites par CI des fonctions encore **non testées** ; un build compilé ne prouve pas qu'un service tiers fonctionne.

## Étude de code Windows / Android

Les deux projets partagent **1 590 fichiers de même chemin**, dont **1 307 identiques** et **283 différents**. Il existe aussi des fichiers de plateforme spécifiques. Une différence de code n'est pas forcément un bug : Windows a des fonctions souris/fenêtre et Android un lecteur et une navigation tactiles.

| Domaine | Windows | Android téléphone | Niveau de vérification |
|---|---|---|---|
| Identité visuelle Kovelyx | Oui | Logos et splash comparés automatiquement | Audit CI |
| Français | Traductions intégrées | Android mobile couvre toutes les clés de base vérifiées (2 410 / 2 410) ; TV dispose de 3 144 / 3 144 clés françaises | Contrôle statique et CI ; textes dynamiques à tester |
| Installateur et mises à jour | EXE dans GitHub Releases | APK généré via Github Actions ; versions publiées historiquement | Publication à revérifier ; signature stable non configurée |
| Profils | Sélection, fond/avatar **local** via explorateur de fichiers | Profils et URLs d'image, mais pas encore le sélecteur de fichier local équivalent | **Écart réel** |
| Trakt/MDBList | Gestion OAuth / identifiants | Paramètres OAuth locaux ajoutés | Code compilé ; connexion réelle à tester |
| Personnalisation et menus | Options desktop avancées | UI adaptée au tactile ; fonctions pas totalement identiques | Parité **non validée** |
| Lecture et extensions | Dépendent du lecteur natif et des sources | Dépendent du lecteur Android, du réseau et des extensions | Tests réels non exhaustifs |
| Debrid | Implémentation héritée Torbox/Premiumize/Real-Debrid | Implémentation héritée identique pour le cœur | La demande **AllDebrid prioritaire** n'est **pas** réalisée dans ces moteurs |

**Conclusion Windows/Android :** les sources et les fonctionnalités se recoupent largement, mais il serait **faux** de dire que toutes les fonctions et boutons Android sont déjà exactement ceux de Windows. Avant une version Android stable : tester réellement import d'images, API, débrid, lecture, recherche, extensions et profils sur appareil.

## Android TV / TV Box

| Critère | État |
|---|---|
| Projet | `apps/tv` du même monorepo, UI Compose prévue pour télécommande |
| Installation | App ID stable `com.kovelyx.tv`, préversion de test `com.kovelyx.tv.debug` |
| Android minimum | API 24 (Android 7.0), Android TV / Google TV et box compatibles ; matériel nécessaire à vérifier |
| TV Leanback | `LEANBACK_LAUNCHER`, mode sans écran tactile requis |
| Deep links | `kovelyx://` déclaré dans le manifeste ; parseur accepte également les anciennes URLs importées |
| APK universel | Configuration Gradle `isUniversalApk = true`, architectures armeabi-v7a, arm64-v8a, x86, x86_64 |
| Tests CI | Build TV et test Kotlin réussis sur une révision de référence ; émulateur Google TV et touches de télécommande validés pour la première préversion TV publiée. Les changements ultérieurs exigent un nouveau build |
| Traductions TV | **3 144 chaînes françaises sur 3 144 chaînes par défaut**, sans clé manquante, avec contrôle CI dédié. Les textes de compte historique et certaines traductions de bibliothèques externes doivent encore être évalués en situation |
| Lecteur/streaming | Build seul insuffisant : des tests avec des sources autorisées, audio, HDR, sous-titres, 4K et performances sont nécessaires |
| Comptes | En attente comme demandé ; pas de promesse de sync Kovelyx active |

Les librairies natives Android TV sont empaquetées dans le dépôt mais leur **reconstruction à partir des sources** et l'absence de connexions réseau tierces restent à auditer.

### CI actuelle

- [Compilations Android et Android TV](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)
- [Test sur émulateur Android TV](https://github.com/anderson76389/Kovelyx/actions/workflows/android-tv-smoke.yml)
- [Publication TV conditionnée à un test réussi](https://github.com/anderson76389/Kovelyx/actions/workflows/publish-tv-preview.yml)
- [Releases Kovelyx](https://github.com/anderson76389/Kovelyx/releases)

Les APK Debug GitHub Actions ne disposent pas encore d'une clé de signature stable. L'installation sur une ancienne préversion peut exiger une désinstallation préalable avec **risque de perte de données locales**.

## Complément de vérification : identité, indépendance et tests complets

**Périmètre vérifié :** les trois projets du même dépôt (`apps/desktop`, `apps/mobile`, `apps/tv`). Ne pas confondre compilation, lancement sur émulateur, tests unitaires, lecture multimédia réelle et disponibilité des comptes.

### Ressources françaises

| Client | Clés de traduction française présentes | Pluriels Android TV |
|---|---:|---|
| Windows | 2 484 / 2 484 dans le fichier de base | sans objet |
| Android mobile | 2 410 / 2 410 dans le fichier de base | sans objet |
| Android TV | 3 144 / 3 144 | 5 / 5 |

Ces chiffres attestent la **couverture des clés contrôlées**, pas la traduction de chaque réponse d'API, message généré par Kotlin ou titre en ligne. Les contrôles XML, doublons, formatage et pluriels TV sont exécutés sur GitHub. Les mentions « Nuvio Sync » restantes sont **des références honnêtes au compte historique**, à remplacer seulement quand le système Kovelyx sera opérationnel.

### Indépendance Nuvio : ce qui a effectivement changé

- Les liens **sortants** vers les œuvres et les téléchargements Android mobile utilisent désormais `kovelyx://` comme Windows. La lecture des anciens `nuvio://` est conservée pour importer des liens existants, sans connexion réseau au projet d'origine.
- Les callbacks Infuse utilisent aussi `kovelyx://` ; la compatibilité entrante avec les anciennes URLs reste volontaire.
- Les configurations Android incluses dans le projet Desktop ne pointent plus vers le dépôt GitHub NuvioMedia pour les mises à jour.
- Sur TV, les pages confidentialité/conditions, l'URL réseau factice, la configuration exemple, les textes de marque et le sponsor hérité ont été corrigés. Le **magasin de clés Release** ne dépend plus du fichier `nuviotv.jks` ni de mots de passe historiques ; une **clé Kovelyx privée, stable et créée par le propriétaire** est indispensable pour signer les futures versions de production.
- Les APK compilés embarquent des bibliothèques natives et les fichiers locaux peuvent porter des noms hérités (`com.nuvio.*`, `libnuvio_engine`) : **ce ne sont pas en eux-mêmes des appels à un serveur Nuvio**, et les renommer sans migration peut corrompre les données ou casser JNI.
- Les scripts de contrôle réseau statiques Android/TV passent, mais ils **n'inspectent ni les paquets DNS/HTTPS réels ni l'intégralité des binaires natifs**. Aucune affirmation d'indépendance absolue sur cette seule base.

### Bloqueurs pour une vraie certification « 100 % fonctionnelle »

| Fonction | État technique | Ce qu'il manque |
|---|---|---|
| Démarrage mobile et TV | Compilations et lancements sur émulateur réussis sur une version de référence | Tester les dernières modifications et appareils physiques |
| Lecteur et sous-titres | Sources natives et interfaces compilées | Vidéos locales/autorisées, codecs 4K/HDR/DV, sous-titres intégrés et externes, lecture prolongée |
| Catalogues, recherche et import | Modules présents | Scénarios HTTP/API de bout en bout, clés réellement configurées, réponse serveur |
| Trakt et MDBList | Intégration prévue dans les sources | OAuth dans le navigateur/TV, tests avec comptes consentants et clés valides |
| Débrid | Moteurs hérités partiellement présents | Implémenter **AllDebrid prioritaire puis Real-Debrid**, tester cache et résolution sur chaque plateforme ; retirer TorBox proprement |
| Profils locaux | Présents ; sélection locale des images Windows | Équivalent du choix de fichiers pour photo et fond Android, sauvegarde et persistance testées |
| Mises à jour Android | Téléchargement dans le code depuis GitHub Kovelyx | **Problème découvert :** les tags de Releases `android-preview-<sha>` / `tv-preview-<sha>` ne sont pas des versions sémantiques acceptées par les sélecteurs actuels ; détection des nouvelles versions et installation sans perte de données **non garanties** |
| Compte unique et synchronisation | Non déployés | Serveur indépendant, migration et synchronisation : explicitement reportés à la fin |
| Reconstruction vidéo depuis les sources | Sources et binaires locaux archivés | Reconstruire et comparer tout le chemin natif P2P, libtorrent/MPV et bibliothèques natives sans dépôts historiques |
| Interface 100 % française | Clés de base couvertes | Captures écran et tests visuels de chaque menu et texte dynamique |

**Suite de tests élargie :** [GitHub Actions — vérification Kotlin mobile et TV](https://github.com/anderson76389/Kovelyx/actions/workflows/functional-verification.yml). Elle exécute des tests unitaires plus larges que les tests de lancement, mais ne remplace pas les scénarios externes réels. Un job en cours ou échoué ne doit pas être présenté comme réussi.

### Critère strict d'indépendance

Un jour où l'organisation NuvioMedia, ses domaines et son dépôt disparaissent, les versions installées de Kovelyx doivent continuer à lire les sources autorisées, ouvrir les catalogues configurés, enregistrer la bibliothèque locale et vérifier leurs mises à jour **sans appeler aucun domaine Nuvio**. Les comptes historiques sont l'exception temporaire convenue, **pas un composant Kovelyx indépendant**. Pour le certifier, lancer les APK et l'EXE avec les domaines Nuvio bloqués, capturer le trafic, refaire les tests multimédias et vérifier la reconstruction native dans un environnement propre.

## Priorités avant de déclarer la parité fonctionnelle

1. **Réalisé sur la préversion initiale :** compiler, installer sur émulateur, vérifier le démarrage et le D-pad, puis publier l’APK dans **Releases**. À répéter pour les changements ultérieurs.
2. Réaliser une première session réelle sur une TV Box (démarrage, navigation télécommande, codecs, sous-titres, réseaux).
3. **Couverture française des ressources TV terminée (3 144 / 3 144)** ; poursuivre la relecture de qualité linguistique, les textes dynamiques non issus des ressources et les comptes historiques, en conservant les licences.
4. Rendre les profils locaux Android équivalents à Windows, avec sélecteur de photos/fonds local qui sauvegarde les médias correctement.
5. Unifier la politique de fournisseurs debrid et respecter les choix AllDebrid et Real-Debrid, en s'appuyant sur des implémentations **réelles**.
6. Ajouter une signature pérenne pour les APK avant de promettre des mises à jour Android TV ou téléphone sans perte de données.

**Les comptes Kovelyx indépendants restent expressément hors périmètre pour cette étape.**
