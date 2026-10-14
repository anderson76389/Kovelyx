# Kovelyx — État technique et indépendance

_Mise à jour du 14 octobre 2026. Ce fichier distingue le code présent de ce qui est réellement utilisable._

## Définition de l'indépendance

**Indépendance à l'égard de Nuvio** : conserver les sources nécessaires et pouvoir compiler, signer, installer et mettre à jour chaque application sans que les dépôts, domaines, comptes ou binaires de NuvioMedia existent encore.

**Indépendance de tous les services** : impossible à garantir pour une application qui interroge des catalogues, hébergeurs, addons, fournisseurs de métadonnées et sites tiers. Les réseaux, systèmes d'exploitation et services de distribution demeurent des dépendances. Pour l'usage 100 % hors ligne, il faudrait un lecteur de fichiers locaux avec une bibliothèque et des métadonnées stockées localement, ce qui est un autre mode fonctionnel.

## État des composants

| Élément | Vérification actuelle | Ce qu'il reste à faire |
|---|---|---|
| Sources TV | Import Git effectué | Nettoyer les dépendances à Nuvio et tester sur téléviseur |
| Sources Android | Import Git effectué | Tester sur téléphone réel |
| Sources Windows | Import Git effectué | Faire tourner réellement le MSI et valider MPV / WebView2 |
| Nom et icône Android | Nom Kovelyx, nouvelle icône manifeste et bannière TV ajoutés | Vérifier les écrans internes, thèmes et ressources encore Nuvio |
| Site de téléchargement | Sources + GitHub Pages publiés | Continuer les tests responsive et de navigation |
| APK Android / TV | Les deux compilations de référence ont réussi au moins une fois | **Nouvelle compilation avec logo Kovelyx non encore certifiée**, tests sur appareils |
| Windows | Archive MPV de 115 320 296 octets restaurée, SHA-256 validé | Windows MSI à compiler et installer, vérifier licence/source du binaire |
| Préversions GitHub | Workflow automatique créé | Vérifier son exécution et la validité des téléchargements |
| Backend privé | Aucun serveur Kovelyx demandé | S'assurer que tous les clients démarrent sans connexion obligatoire à Nuvio |
| Services distants Nuvio | **Non supprimés** ; des URLs et noms subsistent dans le code | Auditer, réécrire ou rendre optionnels tous les appels |
| Addons, catalogues et médias | Toujours dépendants de leurs fournisseurs | Afficher les limites, gérer les erreurs réseau et le cache local |

## Cas particulier : MPV Windows

Le runtime `libmpv-2.dll` a été sauvegardé, avec contrôle de son empreinte cryptographique :

- Version source identifiée : `NuvioMedia/NuvioDesktop`, commit `51963ee4d83057ef08836f4ed9ab68037e081dd3`.
- Taille : **115 320 296 octets**.
- SHA-256 : `07c68bb211f23a218ded0a36eb12207dc3aeb44e5318ffca6ce9dcc7c3173906`.
- L'archive temporaire `kovelyx-windows-mpv` est disponible comme **artefact GitHub Actions**, conservé jusqu'à 90 jours.
- **Elle n'est pas autonome à long terme** : son origine est encore Nuvio et le code source exact de construction du binaire doit être identifié. Ne pas publier un MSI contenant ce binaire avant vérification de sa conformité aux licences.
- Le SDK Microsoft WebView2 du build Windows est téléchargé depuis NuGet à la version épinglée `1.0.2739.15`.

Une future chaîne entièrement indépendante nécessitera une construction MPV reproductible à partir des sources ouvertes officielles, la conservation des licences, du code correspondant et des dépendances.

## Livraisons

- [Compilations Android + TV + diagnostic Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)
- [Sauvegarde technique MPV Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/native-windows.yml)
- [Préversions APK automatiques](https://github.com/anderson76389/Kovelyx/actions/workflows/android-preview.yml)
- [Audit des références externes](https://github.com/anderson76389/Kovelyx/actions/workflows/dependency-audit.yml)
- [Releases Kovelyx](https://github.com/anderson76389/Kovelyx/releases)
- [Site GitHub Pages](https://anderson76389.github.io/Kovelyx/)

## Critères avant la mention « version fonctionnelle »

1. **Compiler** chaque installation au SHA identifié, sans secrets partagés du projet d'origine.
2. **Installer** et lancer sur Windows 64 bits, Android et Android TV réels.
3. **Tester** recherche, catalogue, addon, lecture d'un flux autorisé, audio, sous-titres, historique local, changement de thème, paramètres et redémarrage.
4. **Débrancher tous les domaines Nuvio** pendant les tests ; les fonctionnalités locales doivent fonctionner sans eux.
5. **Documenter les API tierces** (TMDB, Trakt, extensions, etc.) et leur configuration facultative.
6. **Conserver les sources des binaires embarqués**, les licences et les avis légaux adaptés.
7. **Garantir la signature et les mises à jour** avec des clés détenues par Kovelyx, pas les clés Nuvio.
8. **Archiver deux copies indépendantes** des sources et outils, dont au moins une hors GitHub.

**À ce stade, l'indépendance totale n'est pas encore atteinte.** Aucun fichier README ou bouton du site ne doit affirmer le contraire.
