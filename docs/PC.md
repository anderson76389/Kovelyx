# 🖥️ Guide PC — Kovelyx Desktop

Ce guide concerne **uniquement Windows et Linux**. Les anciennes sources Android et TV sont conservées dans Git pour ne pas perdre l'historique, mais elles ne font pas partie de la priorité actuelle.

## Quel fichier choisir ?

| Situation | Fichier conseillé | Avantage |
|---|---|---|
| Installer facilement sur Windows 10/11 x64 | `Kovelyx-Windows-Setup-….exe` | **Un fichier** à lancer pour installer Kovelyx |
| Installation MSI traditionnelle | `Kovelyx-Windows-….msi` | Paquet standard Windows |
| Garder une copie sans installation classique | `Kovelyx-Windows-Portable-….zip` | Dossier portable, mais il contient **de nombreux fichiers nécessaires** |
| Utiliser Linux x86-64 | `Kovelyx-Linux-x86_64-….AppImage` | **Un seul fichier exécutable** ; lancement sans installateur classique |

**Un seul fichier téléchargé ne veut pas dire un seul fichier utilisé en mémoire** : le runtime Java, les extensions natives et les bibliothèques restent nécessaires. Le `Setup.exe` les installe automatiquement. Une AppImage les rassemble autant que possible mais peut encore dépendre de bibliothèques Linux du système.

## Où récupérer les fichiers ?

- [Build Windows validé (EXE, MSI, portable)](https://github.com/anderson76389/Kovelyx/actions/runs/38509512148/artifacts/11869860908)
- [Compilations Windows](https://github.com/anderson76389/Kovelyx/actions/workflows/windows.yml)
- [Build Linux validé (AppImage x86-64)](https://github.com/anderson76389/Kovelyx/actions/runs/38510728469/artifacts/11869259804)
- [Compilations Linux](https://github.com/anderson76389/Kovelyx/actions/workflows/linux.yml)
- [Releases](https://github.com/anderson76389/Kovelyx/releases) — uniquement les versions effectivement publiées
- [Site Kovelyx](https://anderson76389.github.io/Kovelyx/)

Les artefacts Actions ne sont pas des Releases publiques et **peuvent expirer**. Le paquet de test doit provenir d'une compilation verte. Les fichiers ZIP téléchargés depuis l'interface Actions sont des **conteneurs de téléchargement** : il faut d'abord les ouvrir pour obtenir l'EXE ou l'AppImage.

## Première ouverture sur Windows

1. Installer via `Kovelyx-Windows-Setup-….exe` (ou choisir la version portable et extraire tous ses fichiers).
2. Vérifier le nom Kovelyx dans le menu Démarrer et l'icône.
3. Ouvrir l'application en mode invité si aucun serveur de compte n'est configuré.
4. Créer un profil et tester une image depuis **Profils → Modifier → Choisir une photo depuis mon PC**.
5. Tester un fond depuis le même écran ; sauvegarder et redémarrer pour vérifier la persistance.
6. Ajouter les extensions **que vous êtes autorisé à utiliser**, essayer une vidéo, les pistes et les sous-titres.

Les photos personnelles sont copiées dans `%APPDATA%\Kovelyx\profile-media` et ne sont pas partagées automatiquement avec un compte en ligne.

## Sécurité des téléchargements

Chaque artefact généré par le workflow contient des fichiers `.sha256`. Avant d'installer une archive téléchargée, vérifier son origine GitHub et comparer les empreintes :

```powershell
Get-FileHash -Algorithm SHA256 .\Kovelyx-Windows-Setup-*.exe
```

Les exécutables expérimentaux peuvent être **non signés**. Cela peut déclencher un avertissement SmartScreen ; cela ne prouve ni qu'ils sont sûrs ni qu'ils sont malveillants. Ne désactivez pas les protections Windows pour tester.

## Données et mises à jour

- **Paramètres Windows :** `%APPDATA%\Kovelyx`
- **Cache Windows :** `%LOCALAPPDATA%\Kovelyx\Cache`
- **Images personnelles Windows :** `%APPDATA%\Kovelyx\profile-media`

Faire une **copie** de ces données avant un changement important de version. Un `Setup.exe` ne garantit pas à lui seul un mécanisme de mise à jour automatique : la logique de mise à jour doit être testée.

## Tester et signaler

Utiliser le [plan de vérification PC](./TESTS_PC.md). Pour chaque anomalie, joindre la version du fichier, les étapes de reproduction, le résultat attendu, le résultat constaté et les journaux non sensibles. La compilation verte n'est **pas** une certification de toutes les fonctionnalités.

## Indépendance et licences

Le code est dérivé de NuvioDesktop (GPLv3). Certains binaires natifs et liens hérités restent à auditer. Voir [provenance](./PROVENANCE.md) et [audit des dépendances](./AUDIT_DEPENDANCES.md). Le logiciel n'inclut pas de contenus tiers.
