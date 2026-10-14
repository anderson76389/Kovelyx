# Télécharger Kovelyx

Ce dépôt fournit une page de téléchargement statique depuis `docs/index.html`. Elle **ne crée pas de faux téléchargements** : elle consulte la liste des Releases GitHub et n'affiche un bouton actif que lorsqu'un APK ou MSI existe réellement.

## Publication du site (branche gh-pages)

Le site est maintenu dans `docs/` sur `main` et synchronisé automatiquement dans la branche [`gh-pages`](https://github.com/anderson76389/Kovelyx/tree/gh-pages) par le workflow [Publier le site Kovelyx](https://github.com/anderson76389/Kovelyx/actions/workflows/pages.yml).

**GitHub Actions a confirmé la synchronisation et GitHub Pages a également signalé un déploiement réussi.** L'adresse cible est **https://anderson76389.github.io/Kovelyx/**.

Si ton navigateur n'affiche pas encore le site, vérifie une fois les paramètres [Settings → Pages](https://github.com/anderson76389/Kovelyx/settings/pages) :

1. **Build and deployment** → **Source : Deploy from a branch**.
2. **Branch : gh-pages**, dossier **/(root)**, puis **Save**.
3. Laisse GitHub terminer son propre workflow `pages build and deployment`.

**Ne sélectionne pas `GitHub Actions` comme source pour cette méthode.** Le workflow Kovelyx sert à mettre les fichiers à jour dans la branche `gh-pages` ; GitHub Pages se charge ensuite de l'hébergement. Aucun serveur personnel n'est nécessaire.

## Où seront les vrais fichiers ?

- **Android TV :** `Kovelyx-TV-vX.Y.Z-beta.N.apk`
- **Android mobile :** `Kovelyx-Mobile-vX.Y.Z-beta.N.apk`
- **Windows :** `Kovelyx-Windows-vX.Y.Z-beta.N.msi`

Les noms correspondent exactement aux fichiers publiés par le workflow [Publier une préversion](../.github/workflows/publish-test-release.yml). Cette publication est **manuelle** et nécessite un artefact issu d'un build réussi, la validation du testeur et la vérification des droits de redistribution.

## Pourquoi les téléchargements sont-ils indisponibles ?

Une copie du code source ne suffit pas à créer des exécutables. Les premières compilations ont notamment rencontré :

- Android TV : un fichier de signature de l'application d'origine absent. Le build de débogage a été configuré pour utiliser la signature de développement.
- Android : le fichier de configuration locale facultatif était traité comme obligatoire par Gradle ; la déclaration a été corrigée.
- Windows : une DLL vidéo native d'environ 115 Mo manque dans Git. Elle a été inventoriée avec son empreinte SHA-256 ; il faut une restauration vérifiée et conforme aux licences avant la génération d'un MSI.

Les deux compilations Android et Android TV ont déjà réussi sur un build de référence ; cela **ne garantit pas encore leur bon fonctionnement sur appareil réel**. Windows reste en attente de sa bibliothèque native. Le résultat réel est visible dans [Actions](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml).

## Attention aux préversions

Les builds de débogage Android sont destinés aux essais et ne possèdent pas encore nécessairement une clé de signature de distribution stable. Il peut être nécessaire de désinstaller une version de test avant d'en installer une autre, entraînant la perte de ses données locales. **Ne pas s'en servir comme application principale sans sauvegarde préalable.**

Les Releases sont un stockage de fichiers applicatifs. Elles n'impliquent aucune synchronisation en ligne entre appareils ni serveur personnel.
