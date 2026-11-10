> **Bilan actualisé du 10 novembre 2026 :** lire [l’audit des sources et des trois applications v1.1.1078](./AUDIT_INDEPENDANCE_2026-11-10.md), validé dans la PR #109. Les constats ci-dessous sont historiques et ne décrivent pas tous l’état actuel.

# Contrôles d'indépendance Kovelyx — Windows, Android et Android TV

## État vérifiable

Le code actif de la branche principale utilise un backend de compte et de synchronisation Kovelyx basé sur Cloudflare Workers et D1. Les anciens clients de comptes ont été retirés des trois applications. Les tests statiques stricts du dépôt vérifient leur non-réintroduction.

La Release unifiée [v1.1.1045](https://github.com/anderson76389/Kovelyx/releases/tag/v1.1.1045) fournit les trois plateformes depuis le commit `5d2b8e8ad660ffbbe4df0cc6d338489aa341c75c`. Cette version **précède** les renommages de fichiers natifs réalisés ensuite : ne pas confondre source de la Release et contenu d'une pull request non fusionnée.

Preuves GitHub Actions :
- [Audit statique et compilation sans les domaines de l'amont](https://github.com/anderson76389/Kovelyx/actions/runs/42124087167)
- [Suite Android et TV (deuxième tentative réussie)](https://github.com/anderson76389/Kovelyx/actions/runs/42124087214)
- [Compilation des deux APK et publication unifiée](https://github.com/anderson76389/Kovelyx/actions/runs/42124087262)
- [Installateurs Windows](https://github.com/anderson76389/Kovelyx/actions/runs/42124087233)

## Noms des composants locaux

Les noms des archives AAR/JAR natives peuvent être renommés dans les sources et Gradle sans modifier leur contenu binaire. **Cela ne transforme pas** les identifiants embarqués, les API natives ni les informations de licence. La provenance légale des composants et les engagements de redistribution demeurent dans [PROVENANCE.md](./PROVENANCE.md).

Les chemins de package historiques `com.nuvio.*`, les symboles de bibliothèques `nuvio_engine`, les identifiants d'installations et les clés de stockage existantes ne doivent pas subir de remplacement global : un changement incompatible peut casser l'application, le moteur vidéo, les mises à jour ou la récupération des données existantes.

## Limites et validation restante

Les compilations avec les domaines historiques bloqués démontrent l'absence de dépendance *nécessaire à la compilation*. Elles ne suffisent pas à prouver l'absence de tout appel réseau lors d'une lecture réelle, d'une connexion ou d'une synchronisation. Il reste à contrôler les connexions DNS/TLS et les fonctions critiques sur appareils Android, Android TV et Windows installés.

La suppression d'un fichier dans une nouvelle révision Git n'efface **pas** les commits, tags ni Releases déjà publiés. La suppression définitive d'objets historiques demande une opération de réécriture d'historique, une gestion des tags/archives et une validation de l'impact sur les mises à jour. Elle ne doit pas être effectuée pendant une simple opération de renommage.
