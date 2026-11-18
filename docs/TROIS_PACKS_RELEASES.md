# Kovelyx — quatre installateurs directs

À partir de **v1.1.1169**, chaque Release officielle propose **un installateur direct par plateforme**, sans archive ZIP à décompresser :

| Plateforme | Fichier à télécharger | Installation |
| --- | --- | --- |
| Android téléphone et tablette | `Kovelyx-Android-vX.Y.Z.apk` | Ouvrir l’APK pour mettre à jour ou installer |
| Android TV / Google TV | `Kovelyx-TV-vX.Y.Z-universal.apk` | Installer l’APK TV, distinct du mobile |
| Windows 10 / 11 x64 | `Kovelyx-Windows-Setup-vX.Y.Z.exe` | Exécuter directement l’installateur |
| Linux x86_64 | `Kovelyx-Linux-x86_64-vX.Y.Z.AppImage` | Rendre exécutable et lancer l’AppImage |

**Quatre petits fichiers `.sha256`** accompagnent ces quatre installateurs. Ce sont des **empreintes de sécurité**, pas des applications à télécharger manuellement. Ils sont indispensables aux anciens updaters déjà installés : les supprimer empêcherait une mise à jour vérifiée depuis les versions v1.1.1167 et v1.1.1168. La Release compte ainsi quatre installateurs utilisables et quatre contrôles cryptographiques.

La publication est refusée si un installateur manque, si sa taille ou sa signature est incorrecte, si un SHA-256 local ne correspond pas à l'asset GitHub, ou si les quatre builds ne proviennent pas du même commit validé.

## Compatibilité des installations antérieures

- **v1.1.1167** est la Release de transition permanente ; elle contient les anciens formats et les APK compatibles avec les signatures déjà installées. Elle est protégée du nettoyage des Releases.
- **v1.1.1168** est la Release à quatre archives ZIP par plateforme. Son updater sait aussi installer les fichiers APK/EXE/AppImage publiés directement, à condition de disposer des `.sha256`.
- **v1.1.1169 et versions suivantes** utilisent les installateurs directs et leurs empreintes associées. Aucun désinstallateur, profil ou cache utilisateur n'est touché par le regroupement des téléchargements.

## Formats secondaires

Pour garder un seul téléchargement principal par plateforme, les nouveaux téléchargements publics n'incluent plus **MSI et ZIP portable Windows**, ni **DEB et RPM Linux**. Ces formats restent accessibles dans les Releases historiques v1.1.1167 et v1.1.1168. Un utilisateur Windows portable ou Linux DEB/RPM ne doit pas remplacer manuellement son installation par un autre format sans choisir une migration adaptée.

L’AppImage cible **Linux x86_64** et reste dépendante de certaines bibliothèques système : ce n’est pas un paquet natif universel pour Linux ARM64, Flatpak ou toutes les distributions.

Ces choix ne modifient pas l'architecture des comptes Kovelyx et n'ajoutent aucune dépendance opérationnelle à Nuvio. Les tests automatisés vérifient les fichiers et les contrats de mise à jour ; un essai sur chaque appareil physique reste nécessaire avant de parler de compatibilité totalement démontrée.
