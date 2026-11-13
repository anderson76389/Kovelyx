# Kovelyx sur Linux — même application PC que Windows

La version Linux utilise les **mêmes sources Kotlin Multiplatform / Compose Desktop**
que Windows : interface, bibliothèque, profils, moteur de lecture et synchronisation.
Les composants natifs du lecteur sont compilés pour Linux (JNI + libmpv) ;
les sources du moteur sont conservées dans le dépôt.

## Formats de téléchargement

Sur [Releases](https://github.com/anderson76389/Kovelyx/releases/latest), chaque nouvelle
version intégrant Linux contient trois fichiers **Linux x86_64**, avec un
fichier SHA-256 distinct pour chacun :

| Fichier | Distribution |
|---|---|
| Kovelyx-Linux-x86_64-vVERSION.AppImage | Linux x86_64 compatible avec AppImage |
| Kovelyx-Linux-amd64-vVERSION.deb | Debian/Ubuntu et dérivées compatibles (amd64) |
| Kovelyx-Linux-x86_64-vVERSION.rpm | Fedora et dérivées compatibles (x86_64) |

Les paquets sont prévus pour **x86_64 (Intel/AMD 64 bits)**. Ce ne sont pas
des binaires ARM64/aarch64 : Raspberry Pi et certains PC ARM nécessitent un
autre build natif. La compatibilité dépend aussi de glibc, du serveur graphique
(X11/Wayland/XWayland), des codecs, du pilote GPU et des bibliothèques du système.

**Le runtime Java est inclus.** Les bibliothèques GTK, WebKitGTK 4.1,
libmpv, GStreamer et leurs codecs restent des dépendances Linux à installer
avec le gestionnaire de paquets. DEB et RPM déclarent leurs dépendances ;
un AppImage n'est pas forcément totalement autonome pour l'audio/vidéo.

## Installation

Vérifier l'empreinte SHA-256 du fichier téléchargé :

    sha256sum -c Kovelyx-Linux-x86_64-vVERSION.AppImage.sha256

AppImage :

    chmod +x Kovelyx-Linux-x86_64-vVERSION.AppImage
    ./Kovelyx-Linux-x86_64-vVERSION.AppImage

Debian/Ubuntu :

    sudo apt install ./Kovelyx-Linux-amd64-vVERSION.deb

Fedora :

    sudo dnf install ./Kovelyx-Linux-x86_64-vVERSION.rpm

Remplacer VERSION par le numéro réel de la Release. Ne pas installer DEB
et RPM simultanément : choisir le format adapté au système.

## Compilation depuis les sources (Linux x86_64)

Prérequis : JDK 21, Android SDK/Gradle si demandé, Python 3.12, compilateur
C++, pkg-config, libmpv-dev, WebKitGTK 4.1-dev, GTK 3, X11, rpm, fakeroot,
cpio et appimagetool.

Le workflow [Kovelyx Linux](../.github/workflows/linux.yml) installe les
dépendances et lance les tests et tâches de packaging sur Ubuntu :

    cd apps/desktop
    ./gradlew :composeApp:desktopTest \
      :composeApp:packageReleaseDeb \
      :composeApp:packageReleaseRpm \
      :composeApp:packageReleaseAppImage \
      -Pnuvio.desktop.versionName=VERSION --no-daemon

La propriété Gradle porte un ancien nom technique conservé pour compatibilité
avec les binaires et scripts ; ce n'est pas une API Nuvio.

## Publications futures sans casser les autres versions

Le workflow Linux génère AppImage, DEB, RPM et leurs tests, puis un artefact
vérifié lié au commit. Le script
[scripts/publish-verified-preview.py](../scripts/publish-verified-preview.py)
attend désormais les quatre preuves du **même commit** : Windows, Linux,
Android et Android TV, plus les contrôles fonctionnels.

Une nouvelle Release doit donc contenir les quatre plateformes ou être
bloquée. Les anciennes Releases ne sont pas modifiées par ce changement.
Les paquets installés n'ont pas encore de mise à jour automatique Linux
éprouvée ; consulter les Releases pour les remplacer.

## Tests manuels avant version stable

Les tests de compilation et de métadonnées ne prouvent pas la lecture de
chaque vidéo. Tester Ubuntu, Debian, Fedora : démarrage et fermeture,
pochettes, lecteurs audio/vidéo, sous-titres, catalogues et extensions,
profils, stockage, veille et consommation RAM/CPU. Sur Wayland, certains
composants peuvent utiliser XWayland. Pour un compte synchronisé, fournir
un backend Kovelyx HTTPS opérationnel, sans dépendance à Nuvio.
