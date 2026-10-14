# Kovelyx — Windows

Application multimédia de bureau du projet **[Kovelyx](https://github.com/anderson76389/Kovelyx)**. **Priorité à Windows x64** ; les versions Android et TV seront abordées ensuite.

## État du développement (14 octobre 2026)

- Le code Kotlin Multiplatform / Compose Desktop est présent et un installateur MSI a déjà été compilé dans GitHub Actions.
- Les contrôles du MSI et la création de l’archive portable sont en cours de stabilisation.
- Le lancement, la lecture, les extensions, les thèmes et les paramètres ne sont **pas encore validés sur un PC réel**.
- Le pipeline Windows produit, une fois toutes les vérifications réussies, un MSI et un ZIP portable avec son environnement Java. Ces fichiers restent des **artefacts expérimentaux**.
- Le runtime vidéo MPV utilise actuellement un binaire archivé dont les sources et la licence doivent être vérifiées avant redistribution publique.

## Compiler sous Windows

Prévoir Java 21, Python 3.12, les outils C++ Visual Studio, le SDK officiel Microsoft WebView2 et les bibliothèques natives vidéo nécessaires.

```powershell
cd apps/desktop
.\gradlew.bat :composeApp:packageReleaseMsi :composeApp:createReleaseDistributable --no-daemon
```

La commande locale n’installe pas automatiquement toutes les dépendances natives. Pour les prérequis du pipeline, consulter [le workflow Windows](../../.github/workflows/windows.yml).

## Téléchargements et indépendance

- [Compilations Windows et artefacts](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
- [Releases Kovelyx](https://github.com/anderson76389/Kovelyx/releases) — préversions seulement après vérification de la licence et des installations
- [État technique](../../docs/ETAT_TECHNIQUE.md) et [audit des services d’origine](../../docs/AUDIT_NUVIO.md)
- [Provenance et licences](../../docs/PROVENANCE.md)

Kovelyx provient initialement du code open source NuvioDesktop, sous GPLv3. L’indépendance complète à l’égard des services, comptes et binaires d’origine **reste à démontrer**. Conserver les crédits et notices applicables.
