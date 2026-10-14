# Kovelyx pour Windows — suivi réel

## Statut au 14 octobre 2026

**Les sources complètes de la version Windows sont dans `apps/desktop/`.**

Une compilation de référence a généré un MSI, mais son nom, son icône et ses répertoires utilisateurs portaient encore l'identité Nuvio. Un workflow Windows séparé prépare une distribution **Kovelyx** et contrôle ses métadonnées avant d'accepter l'artefact.

- [Workflow dédié : Windows / MSI et tests de packaging](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
- [Sources du moteur archivées dans Kovelyx](../vendor/engine/)
- [Tests séparés des sources moteur (C++/JVM)](https://github.com/anderson76389/Kovelyx/actions/workflows/engine-source-test.yml)
- [Audit des anciennes dépendances Nuvio](AUDIT_NUVIO.md)

### Fonctionnalités provenant du code original, conservées

Le projet ne supprime pas volontairement les modules :

- Interface multimédia, recherche, catalogues et bibliothèque
- Extensions/addons compatibles
- Lecteurs et moteurs audio/vidéo, sous-titres
- Profils, préférences, apparence, thèmes
- Intégrations et comptes externes (selon configuration)
- Suivi, reprise de lecture, historique
- Téléchargements et moteur P2P lorsqu'ils sont pris en charge
- Vérification et installation de mises à jour depuis Kovelyx

**Conservé dans les sources ≠ fonctionnement certifié.** Chaque fonction doit être testée sur un PC Windows réel, avec ses dépendances nécessaires. Certains services Nuvio restent référencés et les intégrations Trakt/Simkl nécessitent leurs propres identifiants/redirections OAuth.

## Séparation avec Nuvio

| Élément | Kovelyx |
| --- | --- |
| Produit MSI | `Kovelyx` |
| Fabricant déclaré | `Kovelyx` |
| MSI Upgrade UUID | `3f7af095-1f5e-4d2b-af6c-3f5c12b2c9a1` |
| Données utilisateur | `%APPDATA%\Kovelyx` |
| Cache Windows | `%LOCALAPPDATA%\Kovelyx\Cache` |
| Icône MSI | `kovelyx.ico`, générée depuis [notre script](../scripts/generate-windows-icon.py) |
| Dépôt de mise à jour | `anderson76389/Kovelyx` |
| Rapports de crash | Désactivés par défaut ; pas d'identifiants Sentry Nuvio dans la configuration du projet |
| Distribution | MSI expérimental, éventuellement archive portable suivant les fichiers réellement produits |

Le programme **ne doit pas** remplacer Nuvio ni écraser ses préférences. Les fonctions de reprise/migration depuis Nuvio ne sont pas encore implémentées.

## Préparer une compilation

Sur un système Windows récent disposant de Git, JDK 21, Python 3.12, Gradle, Visual Studio C++, SDK WebView2 et de bibliothèques natives :

```powershell
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx
python -m pip install "Pillow==11.3.0"
python scripts/generate-windows-icon.py --dest apps/desktop/composeApp/src/desktopMain/resources/icons
cd apps/desktop
.\gradlew.bat :composeApp:packageReleaseMsi
```

**Attention :** cette commande ne recrée pas les binaires natifs MPV manquants. La CI restaure provisoirement `libmpv-2.dll` depuis un artefact vérifié appartenant à Kovelyx. Cet artefact est temporaire (limite de rétention) et dépend historiquement d'une distribution initiale de Nuvio.

## Ce qui empêche toujours d'affirmer « 100 % indépendant »

1. **Lecture et P2P :** le code source GPL du moteur C++/JVM est archivé dans `vendor/engine`, mais il faut construire et comparer ses bibliothèques à celles actuellement intégrées. La compilation C++ sans libtorrent ne fournit pas la fonctionnalité P2P complète. Les sources exactes du moteur MPV, de FFmpeg et de leurs dépendances doivent être conservées elles aussi.
2. **Comptes et synchronisation :** certains écrans ou services utilisent encore l'infrastructure Supabase héritée ; la connexion facultative, le mode sans compte et le stockage local doivent être validés.
3. **OAuth et liens internes :** les protocoles `nuvio://` et les identifiants historiques de certaines intégrations doivent être remplacés après vérification des callbacks configurés chez leurs fournisseurs.
4. **Actualisation :** les mises à jour doivent être publiées depuis les Releases Kovelyx, avec leur signature et tests d'intégrité.
5. **Tests réels :** une compilation Gradle réussie ne prouve pas qu'un lecteur vidéo démarre, que le moteur P2P fonctionne ou que tous les addons fonctionnent.
6. **Disponibilité des services :** toute application s'appuyant sur des addons, métadonnées et contenus hébergés ailleurs dépend encore de ces serveurs. Être indépendant de Nuvio ne signifie pas disposer de tous les contenus hors ligne.

## Tests avant première Release

- [ ] Installer le MSI sur un Windows vierge, sans Nuvio.
- [ ] Installer Kovelyx **à côté** de Nuvio : chaque programme doit avoir son dossier et son raccourci.
- [ ] Lancer Kovelyx sans Internet, vérifier l'accueil et les réglages locaux.
- [ ] Vérifier avec des contenus autorisés au moins trois formats vidéo/audio et différents sous-titres.
- [ ] Installer et désinstaller un addon, vérifier la recherche et l'affichage des métadonnées.
- [ ] Tester lecture, volume, plein écran, thèmes, changements de fenêtres, historique et redémarrage.
- [ ] Vérifier que la désinstallation ne supprime pas les données d'une autre application.
- [ ] Inspecter les appels réseau DNS/HTTP : aucun service Nuvio requis.
- [ ] Désactiver les domaines Nuvio puis refaire les tests fonctionnels.
- [ ] Valider les obligations de licence du bundle final et sauvegarder durablement ses sources associées.

**On ne qualifiera pas Kovelyx Windows de complet et totalement indépendant tant que cette liste n'aura pas été validée.**
