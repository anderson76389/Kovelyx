# Kovelyx — télécharger Windows, Android et Android TV

Le site officiel est hébergé gratuitement sur GitHub Pages : **https://anderson76389.github.io/Kovelyx/**.

Les trois applications évoluent indépendamment dans le même dépôt. Le site identifie **les véritables fichiers des Releases**, sans conserver de tag ou de numéro de version obsolète dans les boutons.

| Appareil | Page de téléchargement | Format |
|---|---|---|
| Windows 10/11 x64 | [Télécharger Windows](https://anderson76389.github.io/Kovelyx/?plateforme=desktop#plateformes) | `Kovelyx-Windows-Setup-*.exe`, MSI et ZIP portable |
| Android mobile / tablette | [Télécharger Android](https://anderson76389.github.io/Kovelyx/?plateforme=android#plateformes) | `Kovelyx-Android-*.apk` ou `Kovelyx-Mobile-*.apk` |
| Android TV / Google TV / TV Box | [Télécharger Android TV](https://anderson76389.github.io/Kovelyx/?plateforme=tv#plateformes) | `Kovelyx-TV-*.apk` |

## Comment les téléchargements restent à jour

Au chargement, `docs/site.js` appelle `https://api.github.com/repos/anderson76389/Kovelyx/releases?per_page=100` et trie les Releases par publication. Pour chaque plateforme, il recherche séparément un **asset réel, publié, complet** et provenant des téléchargements GitHub du dépôt Kovelyx. Il préfère le `.exe` au `.msi` dans une même Release Windows et montre aussi le ZIP portable et le MSI s'ils sont présents.

Exemples de tags possibles : `v0.1.377-alpha` (Windows), `android-preview-...` (mobile), `tv-preview-...` (TV). Les futures Releases Android signées sont également reconnues, si elles utilisent les préfixes de noms d'assets prévus.

- **Nouvelle Release d'une plateforme :** au prochain chargement du site, son nouveau fichier devient prioritaire sur l'ancienne version de cette plateforme.
- **Build réussi mais pas de Release :** aucun faux bouton de téléchargement ne s'affiche pour ce build.
- **GitHub indisponible, limite API ou JavaScript désactivé :** les liens de secours renvoient vers la [liste officielle des Releases](https://github.com/anderson76389/Kovelyx/releases), **jamais vers un ancien APK ou EXE**.
- **Publication 403 :** le site ne peut pas inventer une Release manquante ; vérifier les permissions de GitHub Actions ou l'artefact temporaire de build.
- **Fichier absent sur la plateforme :** la carte indique l'absence de fichier publié et renvoie vers Releases plutôt que d'associer un APK TV à Android mobile.

Le site ne met pas automatiquement à jour les applications **déjà installées**. Leur mécanisme de mise à jour intégré et la compatibilité de signatures Android sont des vérifications distinctes.

## Publication automatique du site GitHub Pages

Le code source statique du site est dans `docs/index.html`, `docs/site.css` et `docs/site.js`. À chaque modification dans `docs/`, le workflow [Publier le site](https://github.com/anderson76389/Kovelyx/actions/workflows/pages.yml) synchronise le dossier vers la branche `gh-pages` ; GitHub Pages diffuse ensuite le site.

Paramètre initial : **Settings → Pages → Deploy from a branch → gh-pages → /(root)**. Aucune installation de serveur externe n'est requise.

Le test [Tester le site Kovelyx](https://github.com/anderson76389/Kovelyx/actions/workflows/site-check.yml) vérifie la syntaxe, les ressources, les liens locaux et la sélection simulée d'assets Windows, Android et TV, y compris le comportement de secours réseau. Les tests ne remplacent pas un essai réel d'installation sur chaque appareil.

## Versions de test et signatures

Les anciennes APK Debug ne constituent pas des applications Android signées pour une distribution permanente : elles peuvent avoir un identifiant différent des APK Release. Sauvegarder les paramètres et données avant de changer de canal. Pour les formats, la signature et les limites de publication, consulter [Releases et signatures](./RELEASES.md).

Les logiciels sont des clients multimédias, sans catalogues ni films fournis dans les binaires. Les crédits et notices de licences des composants doivent être conservés.
