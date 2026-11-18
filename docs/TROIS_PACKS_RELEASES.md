# Kovelyx — quatre téléchargements par Release

À partir de **v1.1.1168**, une Release unifiée contient **exactement quatre archives ZIP** :

| Téléchargement | Contenu signé et vérifié | Installation manuelle |
|---|---|---|
| `Kovelyx-Android-vX.Y.Z.zip` | APK universel téléphone et tablette | Décompresser et installer l'APK mobile |
| `Kovelyx-TV-vX.Y.Z.zip` | APK universel Android TV | Décompresser et installer l'APK TV |
| `Kovelyx-Linux-vX.Y.Z.zip` | AppImage, DEB et RPM x86_64 | Décompresser et choisir le format compatible avec la distribution |
| `Kovelyx-Windows-vX.Y.Z.zip` | Installateur MSI, installateur EXE et ZIP portable | Décompresser ; pour une installation classique utiliser EXE ou MSI |

Les archives sont indépendantes de Nuvio. Chaque ZIP contient `SHA256SUMS.txt`
référençant les installateurs qui y sont inclus. Le mécanisme de publication
vérifie l'inventaire exact, l'intégrité de chaque binaire, l'empreinte GitHub
de chaque archive et le commit source **avant** de rendre la Release publique.

Les applications équipées du nouvel updater utilisent le SHA-256 officiel de
l'archive exposé par l'API GitHub, téléchargent le ZIP, sélectionnent le bon
installateur et en vérifient l'empreinte interne. Un APK téléphone n'est jamais
installé sur TV, et inversement. Les utilisateurs qui installent manuellement
les ZIP doivent eux-mêmes choisir le bon fichier.

## Compatibilité avec les mises à jour anciennes

La Release **v1.1.1167 est une version de transition obligatoire**.
Elle conserve les huit installateurs directs ainsi que leurs huit fichiers
`.sha256` pour les versions de Kovelyx qui ne savent télécharger que des
installateurs séparés. Elle inclut le nouvel updater capable de lire les ZIP
spécifiques à chaque plateforme. Elle doit être conservée dans GitHub Releases même lorsque
les anciens fichiers non indispensables sont purgés. Les versions antérieures
passent d'abord par cette version, puis pourront recevoir les Releases à quatre
archives.

Les Releases **v1.1.1166 et antérieures ne sont pas supprimées ni modifiées**
par cette migration.

## Compromis technique

Quatre archives sont plus faciles à trouver, mais **elles ne sont pas plus
légères pour le téléchargement individuel** : télécharger le pack Windows
télécharge le MSI, l'EXE et la version portable, tandis que chaque pack Android ne contient qu'un seul APK adapté à son type d'appareil. L'updater extrait uniquement le bon installateur, puis supprime
l'archive temporaire ; aucune bibliothèque, aucun profil ni sauvegarde ne
sont supprimés.

La compatibilité se vérifie automatiquement en CI. Les tests sur de vrais
appareils Windows, Linux, Android et Android TV restent nécessaires.

Les clés privées de signature ne doivent jamais être présentes dans les ZIP :
seuls les exécutables déjà signés et leurs empreintes sont publiés.
