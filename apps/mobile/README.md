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

Licence : [GPL-3.0](LICENSE).
