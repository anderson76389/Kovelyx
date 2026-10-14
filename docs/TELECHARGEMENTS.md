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

## Préversions Android et TV

Les deux jobs Android enregistrent leurs APK sous l'onglet **Artifacts** du workflow de compilation. Les artefacts sont conservés sept jours.

Le workflow [Publier les APK Kovelyx en préversion](https://github.com/anderson76389/Kovelyx/actions/workflows/android-preview.yml) tente d'ajouter un **APK mobile et un APK TV universel** à une Release clairement marquée _expérimentale_. Il refuse les APK absents ou corrompus.

**Limitation constatée le 14 octobre 2026 :** GitHub a renvoyé `HTTP 403 Resource not accessible by integration` lors de la création d'une Release avec son jeton GitHub Actions, bien que `contents: write` soit déclaré. Les APK existent dans les artefacts, mais la création automatique des Releases peut nécessiter un jeton dédié.

Pour débloquer les Releases en cas de persistance de ce refus :

1. Dans les paramètres de ton compte GitHub, crée un **Fine-grained personal access token** limité au dépôt `anderson76389/Kovelyx`, avec la permission du dépôt **Contents: Read and write** (et les autres permissions demandées uniquement si GitHub les exige).
2. Ouvre les paramètres de **Kovelyx → Settings → Secrets and variables → Actions → New repository secret**.
3. Nom du secret : **`KOVELYX_RELEASE_TOKEN`**. Colle la valeur uniquement dans le champ secret GitHub, jamais dans le code ni dans une conversation.
4. Relance la publication depuis Actions en fournissant un identifiant de compilation dont **les deux jobs Android sont verts**.

La procédure en mode automatique se sert de ce secret s'il existe ; sans lui, elle réutilise le jeton GitHub Actions intégré qui peut rester bloqué par les permissions effectives de ton dépôt.

**Sécurité** : ce jeton permet d'écrire dans le dépôt. Limite sa durée de validité, évite les permissions supplémentaires et révoque-le s'il fuit. Aucun secret n'est requis pour simplement télécharger un APK.

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
