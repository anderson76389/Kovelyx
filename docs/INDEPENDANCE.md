# Indépendance : critères de réussite

**Définition :** Kovelyx reste installable et maintenable si les dépôts, releases, URL, comptes et images `NuvioMedia` disparaissent.

Une copie de sources est un premier pas ; **elle ne suffit pas**.

## État vérifié avant intégration

| Élément | Statut au démarrage | Critère de sortie |
|---|---|---|
| NuvioTV | Origine publique identifiée | Snapshot importé + build Android TV réussi |
| NuvioMobile | Origine publique identifiée | Snapshot importé + build Android réussi |
| NuvioDesktop | Origine publique identifiée ; alpha | Snapshot importé + MSI Windows installé/testé |
| MPVKit | Source publique et commits Gitlink trouvés | Sources vendues localement, build confirmé |
| libmpv-android (TV) | Gitlink sans URL détecté | Source et binaires récupérés légalement, build/test |
| libass-android (mobile/desktop) | Gitlink sans URL détecté | Source et binaires récupérés légalement, build/test |
| MPV natif sur Desktop | Pointeurs Git LFS | Objets binaires sauvegardés sur une infrastructure propre |
| Backend & comptes | Configuration self-host, images Docker externes | Images figées/miroirs + sources/audit des licences |
| Mise à jour | Références Nuvio dans certains clients | Mises à jour sur le dépôt et les releases Kovelyx |
| Marque | Nuvio dans sources, packages et visuels | Identifiants, marque et contenus de Kovelyx distincts |
| Services tiers | APIs de métadonnées / catalogues / addons | Liste explicite, solutions de secours et gestion des pannes |
| Automatisation | Workflows de base | Builds reproductibles, tests, APK et MSI publiés |
| Sauvegarde | GitHub unique | Deuxième sauvegarde indépendante et restauration testée |

## Feuille de route technique

### Phase A — Sauvegarde
1. Importer les snapshots et conserver `LICENSE`, `NOTICE`, etc.
2. Résoudre les gitlinks orphelins.
3. Préserver les objets Git LFS nécessaires et les ressources natives.
4. Documenter les scripts externes, les images Docker et les appels réseau.

### Phase B — Construction
1. Compiler les variantes `fullDebug` Android TV et Mobile.
2. Compiler le paquet MSI Windows depuis un runner Windows.
3. Exécuter des tests fonctionnels sur des appareils réels (télécommande, lecture, formats, sous-titres, extensions, reprise, configurations).
4. Publier uniquement des fichiers effectivement produits et testés.

### Phase C — Kovelyx
1. Définir le logo original et l'identité visuelle.
2. Modifier les identifiants d'app, noms et URLs de mises à jour **sans** changer inconsidérément les espaces de noms du code.
3. Remplacer l'infrastructure Nuvio par des services sous ton contrôle.
4. Migrer les données avec procédures de retour arrière et sauvegarde.
5. Documenter les données qui dépendent toujours de fournisseurs tiers.

## Sécurité

- Aucune clé, URL privée ou compte admin dans Git.
- Une clé d'API embarquée dans un APK **ne constitue pas un secret**, même si le dépôt est privé.
- Toute publication est volontaire et réalisée après contrôle des licences, marques et tests.
- Ne pas affirmer `100 % indépendant` tant que les critères ci-dessus ne sont pas démontrés.
