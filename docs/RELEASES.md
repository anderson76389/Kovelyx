# Kovelyx — téléchargements et publication GitHub

Deux plateformes sont mises en avant : **Windows x64 (.exe)** et **Android 7+ (APK universel de test)**. Les sources TV restent dans le dépôt, mais ne sont pas une troisième distribution publique actuellement.

## Téléchargements réellement vérifiés (15 octobre 2026)

| Plateforme | Fichier / origine | Contrôle | Téléchargement |
|---|---|---|---|
| Windows 10/11 x64 | `Kovelyx-Windows-Setup-v0.1.247-alpha.exe` · GitHub Release | Build Windows réussi, installation réelle à retester selon la machine | [EXE Windows (Release)](https://github.com/anderson76389/Kovelyx/releases/download/v0.1.247-alpha/Kovelyx-Windows-Setup-v0.1.247-alpha.exe) |
| Android 7+ | `Kovelyx-Android-android-preview-4ac59948e05c.apk` · GitHub Actions | Compilation, ZIP, signature, identifiant, installation et démarrage sur émulateur validés | [APK dans un ZIP Actions (temporaire)](https://github.com/anderson76389/Kovelyx/actions/runs/38761977979/artifacts/11986042090) |

L'APK Android est une **seule archive APK pour plusieurs architectures**, car les splits ABI sont désactivés sur la compilation Debug. Ce n'est pas une garantie que tous les téléphones, téléviseurs, flux ou extensions fonctionnent. Android 7+ est requis ; les tests automatiques ont porté sur un émulateur Android 34 x86_64. **Ne pas qualifier ce fichier de version Android TV testée**.

### Différence entre Releases et Artifacts

- **GitHub Releases :** fichiers directement téléchargeables, sans ZIP imposé par GitHub Actions ; recommandés pour la distribution.
- **GitHub Actions / Artifacts :** archive ZIP contenant l'APK à décompresser, accès parfois réservé aux comptes GitHub ; **expiration de la copie actuelle le 14 novembre 2026**.
- Une compilation réussie n'est pas une validation complète des lecteurs, identifiants, services tiers ou appareils réels.

## Pourquoi le nouvel APK n'est-il pas encore dans Releases ?

Le workflow [Publier APK Android](../.github/workflows/publish-android-debug.yml) a vérifié le fichier et sa signature, mais GitHub a refusé l'appel de création de Release avec **HTTP 403 (Resource not accessible by integration)**. Ce problème concerne l'autorisation du jeton Actions et non la validité de l'APK. **L'APK n'a donc pas été publié directement dans une Release.**

**Nouveau test du 15 octobre 2026 :** même après activation des droits de publication annoncée, une nouvelle exécution du workflow a encore renvoyé **HTTP 403** ([journal GitHub Actions](https://github.com/anderson76389/Kovelyx/actions/runs/38785905465)). Le contrôle de l'APK reste vert, mais le job de publication est désormais **rouge** quand la Release échoue, plutôt que de déclarer un faux succès.

Pour débloquer les prochaines publications automatiques, une personne administratrice du dépôt doit choisir **l'une** de ces solutions :

1. Vérifier la configuration **Settings → Actions → General → Workflow permissions → Read and write permissions**. Cette option a été activée, mais elle **n'a pas suffi** lors du dernier test : passer à la solution suivante si le 403 persiste.
2. Créer un **fine-grained personal access token** dans les paramètres de son compte GitHub, avec accès au dépôt **anderson76389/Kovelyx uniquement** et permission **Contents: Read and write** ; choisir une date d'expiration raisonnable. Enregistrer sa valeur dans **Kovelyx → Settings → Secrets and variables → Actions → New repository secret**, nom exact `KOVELYX_RELEASE_TOKEN`. Ne jamais placer cette valeur dans le dépôt ou la conversation. Une fois configuré, relancer le workflow : **Actions → Préparer APK Android et publier dans Releases si autorisé → Run workflow**, en laissant l'identifiant vide.

Une fois l'autorisation réglée :

1. Aller dans **Actions → Préparer APK Android et publier dans Releases si autorisé**.
2. Cliquer sur **Run workflow**, laisser l'identifiant du build vide pour choisir le dernier build Android réussi, ou renseigner `38761027079` pour republier précisément l'APK vérifié du 15 octobre.
3. Le workflow refait les contrôles ZIP, APK et signature et crée une **préversion expérimentale** associée au commit source. Si la création est refusée, les fichiers restent disponibles dans l'artefact et le résumé affiche explicitement la cause.
4. La [page de téléchargement Kovelyx](https://anderson76389.github.io/Kovelyx/) détecte automatiquement la nouvelle Release ; le bouton Android devient un lien direct vers l'APK publié.

Le [workflow Windows](../.github/workflows/publish-windows-preview.yml) utilise également `KOVELYX_RELEASE_TOKEN` s'il est configuré, avec repli sur le jeton GitHub par défaut.

## Signature Android et mises à jour

L'APK actuel utilise une **signature Debug de test**, susceptible de changer entre compilations GitHub Actions. Même si l'APK s'installe, une mise à jour par-dessus une autre préversion peut donc échouer avec une signature incompatible. **Sauvegarder les données et ne pas promettre de mises à jour Android transparentes** jusqu'à la configuration d'une clé de signature Kovelyx stable et privée.

Pour une Release Android de production, il faudra une signature stable, une version de paquet correctement incrémentée et une série de tests fonctionnels sur téléphone et Android TV si TV doit être officiellement pris en charge.

## État de l'indépendance

Le dépôt, les builds, l'identité et les sources sont chez Kovelyx ; l'audit des dépendances héritées de Nuvio est encore en cours. Les notices de licences et attributions doivent être conservées.
