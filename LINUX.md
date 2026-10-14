# 🐧 Kovelyx sur Linux

L'objectif Linux est **une AppImage exécutable x86-64 dans un seul fichier** : pas de dossier de 50 fichiers à conserver à côté du programme. La version Linux est **expérimentale** : sa compilation AppImage et sa structure ont été vérifiées sur Ubuntu 24.04 en CI, mais la lecture vidéo et la compatibilité avec plusieurs distributions doivent encore être testées.

## Fichier principal

`Kovelyx-Linux-x86_64-<commit>.AppImage` — téléchargement depuis le [workflow Linux](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml) **uniquement si l'exécution est verte et qu'un artefact existe**. [Première AppImage contrôlée par le pipeline](https://github.com/anderson76389/Kovelyx/actions/runs/38510728469/artifacts/11869259804).

1. Ouvrir le dernier workflow Linux **réussi**, puis télécharger son artefact `Kovelyx-Linux-AppImage-experimental`.
2. Décompresser **l'archive GitHub Actions** (elle contient l'AppImage et son fichier SHA256). L'AppImage elle-même ne doit pas être décompressée manuellement.
3. Dans un terminal :
   ```bash
   chmod +x Kovelyx-Linux-x86_64-*.AppImage
   ./Kovelyx-Linux-x86_64-*.AppImage
   ```
4. Vérifier l'empreinte du fichier téléchargé avec `sha256sum -c *.sha256`, depuis le dossier qui contient les deux fichiers.

**Compatibilité :** version **x86-64** uniquement. Aucun build ARM64 n'est encore validé. Une AppImage contient le lanceur, l'application et le runtime Java, **mais ne promet pas d'embarquer toutes les bibliothèques du système**.

## Dépendances et limitations Linux

Le lecteur d'origine utilise notamment **libmpv**, **GTK 3**, **WebKit2GTK 4.1**, **X11** et des bibliothèques vidéo. Suivant la distribution, ces bibliothèques peuvent être absentes ou avoir des noms différents.

Sur Ubuntu/Debian, installer les dépendances compatibles disponibles auprès de la distribution et vérifier les erreurs de chargement ; ne téléchargez jamais des `.so` inconnues depuis un site arbitraire. Le [script de dépendances de compilation](../apps/desktop/scripts/linux/install-desktop-build-dependencies.sh) sert au développeur : il installe aussi les en-têtes et outils C++ et n'est **pas** requis en entier pour les utilisateurs.

Le rendu vidéo intégré s'appuie sur des composants liés à X11 : le comportement sous **Wayland pur** n'est pas encore certifié. Un environnement XWayland peut être nécessaire. Fedora, Arch, Ubuntu, Debian et les autres distributions doivent être testées individuellement.

## Compilation

Depuis Linux x86-64, avec **JDK 21** et toutes les dépendances C++ natives :

```bash
git clone https://github.com/anderson76389/Kovelyx.git
cd Kovelyx
bash apps/desktop/scripts/linux/install-desktop-build-dependencies.sh
python3 -m pip install Pillow==11.3.0
python3 scripts/generate-windows-icon.py --dest apps/desktop/composeApp/src/desktopMain/resources/icons
cd apps/desktop
chmod +x gradlew
# Prérequis : appimagetool exécutable et vérifié via SHA256
APPIMAGETOOL=/chemin/absolu/vers/appimagetool-x86_64.AppImage ./gradlew :composeApp:packageReleaseAppImage --no-daemon
```

La commande d'installation des dépendances demande les droits administrateur, **pas le lancement du logiciel**. Pour reproduire précisément la compilation automatisée, lire [`linux.yml`](../.github/workflows/linux.yml).

## Aide au diagnostic

| Symptôme | À vérifier |
|:--|:--|
| `Permission denied` | `chmod +x` sur le fichier AppImage |
| Bibliothèque `libmpv` ou `webkit` introuvable | Paquet natif disponible dans la distribution |
| Fenêtre sans vidéo | Pilote graphique, X11/XWayland, journaux du lecteur |
| `exec format error` | Architecture du PC (x86-64 obligatoire) |
| L'AppImage ne démarre pas | Journal du terminal, distribution et version |

Signaler les erreurs dans les [Issues Kovelyx](https://github.com/anderson76389/Kovelyx/issues) avec la distribution, sa version, le type de session (X11/Wayland), la version du noyau, le processeur et les journaux expurgés de toute donnée sensible.

## Attention

Linux **n'exécute pas les .exe Windows nativement**. Le format à fichier unique conseillé est l'**AppImage** ; les installateurs Windows et Linux ne sont pas interchangeables.
