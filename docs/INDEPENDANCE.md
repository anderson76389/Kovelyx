# Indépendance : critères de réussite

**Définition :** Kovelyx doit pouvoir être installé et maintenu sur Windows, Android et Android TV si les dépôts, Releases et sites NuvioMedia disparaissent. Il **ne doit pas nécessiter de serveur personnel Kovelyx**.

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
| Configuration par appareil | Gestion du stockage local encore à auditer | Préférences, addons et bibliothèque conservés localement sans compte ni backend Kovelyx |
| Mise à jour | Références Nuvio dans certains clients | Mises à jour sur le dépôt et les releases Kovelyx |
| Marque | Nuvio dans sources, packages et visuels | Identifiants, marque et contenus de Kovelyx distincts |
| Services tiers | APIs de métadonnées / catalogues / addons | Liste explicite, solutions de secours et gestion des pannes |
| Automatisation | Workflows de base | Builds reproductibles, tests, APK et MSI publiés |
| Sauvegarde | GitHub unique | Deuxième sauvegarde des sources + export des réglages depuis chaque appareil |

## Feuille de route technique

### Phase A — Sauvegarde
1. Importer les snapshots et conserver `LICENSE`, `NOTICE`, etc.
2. Résoudre les gitlinks orphelins.
3. Préserver les objets Git LFS nécessaires et les ressources natives.
4. Documenter les téléchargements de dépendances, les API externes et les appels réseau.

### Phase B — Construction
1. Compiler les variantes `fullDebug` Android TV et Mobile.
2. Compiler le paquet MSI Windows depuis un runner Windows.
3. Exécuter des tests fonctionnels sur des appareils réels (télécommande, lecture, formats, sous-titres, extensions, reprise, configurations).
4. Publier uniquement des fichiers effectivement produits et testés.

### Phase C — Kovelyx
1. Définir le logo original et l'identité visuelle.
2. Modifier les identifiants d'app, noms et URLs de mises à jour **sans** changer inconsidérément les espaces de noms du code.
3. Supprimer les obligations de compte et backend spécifiques à Nuvio sans casser les fonctions de lecture.
4. Conserver paramètres, extensions et bibliothèque dans le stockage local de chaque appareil ; prévoir un export/import manuel si nécessaire.
5. Documenter les fonctions nécessitant toujours Internet, notamment les addons, catalogues et métadonnées.

## Sécurité

- Aucune clé, URL privée ou compte admin dans Git.
- Une clé d'API embarquée dans un APK **ne constitue pas un secret**, même si le dépôt est privé.
- Toute publication est volontaire et réalisée après contrôle des licences, marques et tests.
- Ne pas affirmer `100 % indépendant` tant que les critères ci-dessus ne sont pas démontrés.
