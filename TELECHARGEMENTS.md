# Télécharger Kovelyx

Ce dépôt fournit une page de téléchargement statique depuis `docs/index.html`. Elle **ne crée pas de faux téléchargements** : elle consulte la liste des Releases GitHub et n'affiche un bouton actif que lorsqu'un APK ou MSI existe réellement.

## Activer GitHub Pages (une seule fois)

La connexion GitHub automatisée n'a pas l'autorisation d'activer Pages à ta place. Dans le dépôt [Kovelyx](https://github.com/anderson76389/Kovelyx) :

1. Ouvre **Settings** → **Pages**.
2. Dans **Build and deployment**, sélectionne **Source → GitHub Actions**.
3. Va dans **Actions** → **Site Kovelyx - GitHub Pages** → **Run workflow** (branche `main`).
4. Après un déploiement réussi, l'adresse publique sera : **https://anderson76389.github.io/Kovelyx/**.

Le site est une simple page statique hébergée par GitHub. **Il ne constitue pas un serveur de streaming ni un serveur de comptes**. Les applications Kovelyx n'ont pas besoin de cette page pour fonctionner une fois installées.

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

Ces corrections n'attestent **pas encore** que tous les builds réussissent ni que l'application fonctionne. Le résultat réel est visible dans [Actions](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml).

## Attention aux préversions

Les builds de débogage Android sont destinés aux essais et ne possèdent pas encore nécessairement une clé de signature de distribution stable. Il peut être nécessaire de désinstaller une version de test avant d'en installer une autre, entraînant la perte de ses données locales. **Ne pas s'en servir comme application principale sans sauvegarde préalable.**

Les Releases sont un stockage de fichiers applicatifs. Elles n'impliquent aucune synchronisation en ligne entre appareils ni serveur personnel.
