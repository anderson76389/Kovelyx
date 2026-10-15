# Kovelyx — Android (préversion)

Version Android mobile de **Kovelyx** issue du même dépôt GitHub que Windows et Android TV.

- **Dépôt du projet :** https://github.com/anderson76389/Kovelyx
- **Téléchargements et versions expérimentales :** https://github.com/anderson76389/Kovelyx/releases
- **Conditions :** https://anderson76389.github.io/Kovelyx/conditions.html
- **Confidentialité :** https://anderson76389.github.io/Kovelyx/confidentialite.html

## Compiler l'APK mobile de test

Sur un système disposant de Java 21 et du SDK Android, à la racine de `apps/mobile` :

```bash
./gradlew :androidApp:assembleFullDebug
```

L'APK est produit dans `androidApp/build/outputs/apk/full/debug/`.

**Attention :** l'APK de débogage est expérimental et signé avec une clé de test. Il ne garantit ni la stabilité de l'application ni une migration des données vers une future APK de production. Le système de compte universel Kovelyx n'est pas encore disponible. Un serveur de compte Nuvio n'est pas requis pour compiler l'APK.

Les noms de packages historiques du code et les crédits de licences peuvent encore citer Nuvio, dont les attributions légales doivent être conservées.

## Comparaison avec Windows et vérification

Les projets Windows/Android partagent leurs modules Kotlin et ressources, mais
ils **ne sont pas identiques fonction par fonction**. Le [rapport de parité](../../docs/ETAT_ANDROID_ET_TV.md)
recense les écarts établis, notamment l'import local des images de profils et
les fournisseurs debrid. Une réussite GitHub Actions prouve que l'APK se compile
et/ou démarre sur émulateur ; elle ne valide pas toutes les fonctions réseau.

Avant une annonce de version stable : vérifier la recherche et l'affichage de
films/séries, le lecteur et les sous-titres, les extensions, les préférences,
l'import des catalogues, les profils, les connexions et la récupération des
mises à jour. **Les comptes Kovelyx seront traités en dernier.**

Les nouvelles builds Android sont générées par le
[workflow Android](https://github.com/anderson76389/Kovelyx/actions/workflows/build.yml)
et leur démarrage vérifié par le
[test d'émulateur](https://github.com/anderson76389/Kovelyx/actions/workflows/android-emulator-smoke.yml).

Licence : [GPL-3.0](LICENSE).
