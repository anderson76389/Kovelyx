# Kovelyx — dossier de finalisation et de maintenance

_Mise à jour du 15 novembre 2026. Ce document est un état vérifiable, pas une garantie d'absence de bugs « à vie »._

## Périmètre final

Kovelyx regroupe quatre distributions : Windows (MSI, EXE, ZIP portable), Linux x86_64 (AppImage, DEB, RPM), Android mobile (APK universel) et Android TV (APK universel). Les versions doivent provenir d'un **même commit**, avec un fichier \`.sha256\` pour chacun des huit installateurs.

La référence avant cette finalisation est [v1.1.1163](https://github.com/anderson76389/Kovelyx/releases/tag/v1.1.1163), commit \`41de96374e4e6fc71cc006d3b22f65855382643c\`. Pour la version suivante, ne présumer aucun numéro ni statut avant [publication effective](https://github.com/anderson76389/Kovelyx/releases/latest).

## Conditions automatiques de publication

- Windows : construction MSI/EXE/portable, tests Desktop, vérification de démarrage du lecteur natif et mesures mémoire de référence.
- Linux : compilation AppImage/DEB/RPM et tests Desktop ; la suppression de l'ancienne AppImage est limitée au backup identifié par le mécanisme de mise à jour.
- Mobile et TV : compilation Android, tests unitaires, identification des paquets, signature et comparaison avec le certificat des APK officiels précédents.
- Synchronisation : tests automatisés des chemins de comptes, de bibliothèque et de catalogues, dont Worker Cloudflare + D1 en environnement local.
- Indépendance : tests de compilation sans les domaines Nuvio ; aucun de ces tests ne prouve le fonctionnement de *tous* les addons et catalogues externes.
- Publication : présence exacte des **huit paquets et huit empreintes**, avec contrôle du SHA-256 de chaque pièce et du SHA du commit de la Release. En cas d'échec, le brouillon reste non publié.
- Conservation : politique des quatre premières et quatre dernières Releases publiée dans \`scripts/prune-releases.py\` ; les archives natives sont protégées.

Voir [les actions](https://github.com/anderson76389/Kovelyx/actions), [les Releases](https://github.com/anderson76389/Kovelyx/releases) et [les tests d'indépendance](./CONTROLES_INDEPENDANCE.md).

## Critères de réception manuelle — non certifiés par GitHub Actions

Ces tests nécessitent des appareils **physiques** et un compte de test. Ne jamais les annoncer comme réussis sans procès-verbal :

1. **Windows 10/11** : 30 à 60 min d'utilisation réelle ; recherche, import JSON, changement de profil, lectures vidéo et sous-titres, bascule plein écran, CPU/RAM, absence de gel ou écran noir ; répéter après mise à jour.
2. **Android mobile** : lancer sur un téléphone réel (par exemple HONOR Magic V6), vérifier téléchargement/installations *par-dessus l'existant*, sous-titres personnalisés et polices, bibliothèques, mises en veille, reprise et stockage.
3. **Android TV / Freebox Pop** : tester à la télécommande, navigation complète des Paramètres, extensions, import JSON, lecteur ExoPlayer, sous-titres, veille/reprise et mise à jour. L'émulateur Google TV **ne remplace pas** la Freebox Pop.
4. **Linux x86_64** : contrôler AppImage, DEB et RPM sur distributions compatibles, puis plusieurs mises à jour successives ; vérifier que seul le précédent exécutable identifié est nettoyé.
5. **Synchronisation multi-appareils** : sur un même compte, créer/deplacer/retirer une œuvre, marquer « vu », reprendre une vidéo, changer un profil, ajouter puis supprimer un catalogue JSON ; vérifier les deux autres appareils, le mode hors ligne et la reconnexion.
6. **Réversibilité** : sauvegarder les données avant mise à jour et vérifier qu'une restauration sur installation propre ne perd pas comptes, profils, historique et clés de signature.

Consigner pour chaque essai le numéro de version, l'appareil, le système, le résultat et les logs **sans informations privées**.

## Ce que « indépendant de Nuvio » signifie

L'objectif est que le code, les APK, les installateurs, les sources du moteur et les mises à jour Kovelyx soient maintenables sans **serveurs, comptes ou dépôts Nuvio**. Les contrôles connus bloquent les anciens domaines pendant certaines compilations et exécutions simulées. Les noms de packages historiques utilisés pour compatibilité des données ne constituent pas, à eux seuls, des appels réseau.

**Cela n'implique pas l'indépendance de tous les services externes.** La synchronisation de comptes dépend encore du backend Kovelyx hébergé sur Cloudflare Workers/D1, sauf migration du backend autonome. Les métadonnées et catalogues peuvent dépendre d'API et services tiers (TMDB, Trakt, MDBList, extensions, sources de médias). Consulter [la portabilité du backend](./PORTABILITE_BACKEND.md).

## Préservation pendant des années

- Conserver une **copie miroir Git complète avec tags**, ainsi qu'une archive indépendante du dépôt et des paquets de référence hors GitHub. Ne pas confondre archive de code et sauvegarde de base de données.
- Sauvegarder chiffré, dans un coffre contrôlé par le propriétaire, les **clés de signature APK**, les secrets GitHub/Cloudflare, les clefs privées et la procédure de restauration. Ne jamais les committer.
- Exporter/sauvegarder régulièrement la base D1 et tout stockage média associé ; effectuer des **tests de restauration** périodiques.
- Archiver les sources et licences des dépendances natives avec leurs versions, scripts de build et empreintes. Prévoir les changements d'API et de systèmes d'exploitation.
- Maintenir des tests de non-régression CI ; publier une nouvelle version seulement après succès et contrôle de tous les installateurs, sans promettre une taille RAM/disque parfaitement constante.
- Conserver les anciennes sauvegardes **avant** toute suppression de branche, Release ou historique.

## Politique de versions

Une Release GitHub marquée « Latest » et assortie de tests automatiques n'est **pas** une certification matérielle. Si les tests physiques ne sont pas réalisés, parler de **version consolidée / candidate finale** plutôt que « zéro bug » ou « 100 % garanti à vie ». Toute régression confirmée bloque la qualification finale et donne lieu à une correction testée puis fusionnée.
