# Coffre local des dépendances de compilation

Les sources Git, les APK et les installateurs ne contiennent pas tous les outils de compilation. Ce coffre conserve les modules Gradle résolus, leurs métadonnées de cache, les distributions Gradle déjà extraites (8.13 TV, 9.4.1 mobile/desktop) et les dépendances Maven Robolectric téléchargées pendant les tests Android. Il ne modifie pas les applications.

## Créer une copie sur chaque système

1. Utiliser Python 3.12+, Java et les SDK nécessaires. Compiler et tester la révision voulue avec les tâches des workflows de ce dépôt. Fermer Android Studio et arrêter les daemons Gradle de cette révision avant la copie. Idéalement utiliser un GRADLE_USER_HOME consacré à Kovelyx, sans dépendances d'autres projets.
2. Depuis le dépôt, exécuter :

       python scripts/build-dependency-vault.py capture /chemin/vers/coffre-kovelyx

   Sous Windows, le chemin peut être par exemple D:\Kovelyx\Build-Windows. Les paramètres --gradle-home et --maven-repo permettent de préciser des caches personnalisés. Le dossier de destination doit être nouveau. La capture ne télécharge pas les artefacts absents : les deux versions Gradle et les JAR Robolectric doivent être effectivement présents pour couvrir tous les builds Android.
3. Vérifier :

       python scripts/build-dependency-vault.py verify /chemin/vers/coffre-kovelyx

4. Copier le dossier (archive et somme SHA-256) sur un disque externe. Répéter sur Linux et Windows, même architecture que le futur environnement. Ne pas publier les caches : ils peuvent contenir des bibliothèques privées. Les fichiers de configuration utilisateur, clés, jetons, paramètres Maven, caches de build et fichiers de verrouillage ne sont pas copiés. L'empreinte garantit l'intégrité, pas l'authenticité de l'origine.

## Restaurer et démontrer la compilation

       python scripts/build-dependency-vault.py restore /chemin/vers/coffre-kovelyx /chemin/vers/restauration

Utiliser une copie propre des sources correspondant à la révision indiquée dans le manifeste et définir GRADLE_USER_HOME sur le sous-dossier gradle-home restauré. Pour les tests Robolectric, définir JAVA_TOOL_OPTIONS avec -Drobolectric.offline=true et -Drobolectric.dependency.dir pointant vers le sous-dossier robolectric-jars restauré (garder les guillemets appropriés si le chemin comporte des espaces). Sous Linux, utiliser une copie des sources avec bit exécutable du wrapper ; sous Windows, gradlew.bat.

Exécuter les tâches Gradle habituelles avec --offline --no-daemon --no-build-cache et --rerun-tasks, par exemple assembleFullDebug et testAndroidHostTest pour le mobile, :app:assembleFullDebug et :app:testFullDebugUnitTest pour la TV, :composeApp:desktopTest et les tâches de packaging du système pour desktop. Consulter les workflows pour les tâches exactes et prérequis natifs. Couper réellement le réseau pour prouver qu'aucun téléchargement hors Gradle n'est nécessaire.

**Limites :** ce coffre n'inclut pas les SDK Android/NDK, JDK, outils C/C++, Kotlin/Native hors cache Gradle, NuGet/WebView2, outils de packaging, binaires externes ni clés de signature. Les sauvegarder séparément selon les licences et les workflows. La capture et sa vérification ne certifient jamais une compilation hors réseau ; le manifeste indique explicitement offline_build_verified=false. Une archive doit être renouvelée après changement de dépendances et testée sur une machine isolée. Elle n'ajoute aucune dépendance runtime ni requête Cloudflare.

## Archiver aussi les outils hors Gradle

À partir d'archives ou installateurs officiels déjà téléchargés, avec leurs licences et empreintes d'origine, ajouter une option répétable `--toolchain-file LABEL=PATH` lors de la capture. Exemple Windows (adapter les chemins, versions exactes à relever dans les workflows) :

    python scripts/build-dependency-vault.py capture D:\Kovelyx\Build-Windows --toolchain-file "jdk=D:\Installateurs\jdk21.zip" --toolchain-file "sdk=D:\Installateurs\android-sdk.zip" --toolchain-file "ndk=D:\Installateurs\android-ndk.zip" --toolchain-file "webview2=D:\Installateurs\webview2.nupkg"

Inclure également les outils de compilation C/C++, packaging Windows/Linux, Kotlin/Native et les binaires nécessaires à la version ciblée. Une archive SDK doit inclure les plateformes, build-tools, platform-tools, licences et NDK nécessaires au build, pas seulement command-line tools. L'option ne télécharge rien et ne prétend pas vérifier que tous ces composants sont présents. Les archives sont restaurées dans `toolchains/<label>/`, non exécutées ni installées automatiquement. Vérifier le contenu et les licences avant archivage. Les clés de signature restent séparées, dans une sauvegarde privée chiffrée.

### Validation finale (à remplir avec des preuves)

- Restaurer le coffre sur Windows et Linux propres, installer les outils depuis leurs archives, bloquer le réseau avant de lancer les tâches Gradle hors ligne.
- Tester la vraie APK sur la Freebox Pop et sur le mobile, les exécutables Windows et Linux, mise à jour sur une version antérieure, données et espace disque avant/après.
- Mesurer CPU/RAM/PSS à froid, après 30 minutes et après 10 heures de lecture du même média légal local, avec pauses et changements d'épisodes. Une mesure de 30 minutes ne valide pas dix heures.
- Tester les trois comptes distincts et leurs sessions, plus la disponibilité réelle des fournisseurs d'épisodes. Le banc D1 local mesure le contrat et l'isolation, jamais les quotas du compte Cloudflare.
- Conserver commit, versions, SHA-256, date, résultats et échecs avec les archives. Ne pas publier de jetons ou données de compte.
