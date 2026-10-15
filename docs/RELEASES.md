# Kovelyx — téléchargements et publication GitHub

Trois plateformes, **un seul dépôt** et des fichiers d’installation distincts : Windows x64, Android mobile et Android TV / TV Box.

## Téléchargements vérifiés dans GitHub Releases — 15 octobre 2026

| Plateforme | Fichier | Preuve disponible | Téléchargement |
|---|---|---|---|
| Windows x64 | `Kovelyx-Windows-Setup-v0.1.309-alpha.exe` | Release avec EXE, MSI et ZIP portable ; tests du programme sur machine réelle toujours nécessaires | [Release Windows](https://github.com/anderson76389/Kovelyx/releases/tag/v0.1.309-alpha) |
| Android mobile 7+ | `Kovelyx-Android-android-preview-0e43cbe05227.apk` | APK de test publié ; la parité fonctionnelle et la mise à jour transparente ne sont pas garanties | [APK Android](https://github.com/anderson76389/Kovelyx/releases/tag/android-preview-0e43cbe05227) |
| Android TV / Google TV 7+ | `Kovelyx-TV-tv-preview-9c6ec281a980-universal.apk` | Compilation signée et test de lancement/navigation basique sur émulateur Google TV réussis | [APK Android TV](https://github.com/anderson76389/Kovelyx/releases/tag/tv-preview-9c6ec281a980) |

Les APK sont **des préversions Debug** : ils doivent encore être testés avec des sources, des profils et des appareils réels. **Ne pas installer la version Android mobile sur une TV Box à la place de la version TV.** Les deux utilisent des interfaces et des identifiants différents.

### Différence entre Releases et Artifacts

- **GitHub Releases :** fichiers directement téléchargeables, sans ZIP imposé par GitHub Actions ; recommandés pour la distribution.
- **GitHub Actions / Artifacts :** archive ZIP contenant l'APK à décompresser, accès parfois réservé aux comptes GitHub ; date d’expiration propre à chaque artefact.
- Une compilation réussie n'est pas une validation complète des lecteurs, identifiants, services tiers ou appareils réels.

## Erreur de publication Android 403 — historique résolu

Les anciens workflows ont rencontré des erreurs HTTP 403 lors de la création de Releases. **Ce blocage n’est plus général : les préversions Android mobile et TV figurent bien dans Releases.** Les journaux anciens restent disponibles à titre de diagnostic, mais il n’est pas nécessaire de configurer une nouvelle clé d’accès simplement pour télécharger les versions actuellement publiées.

Si une **nouvelle** publication échoue avec une erreur 403, vérifier les droits `Settings → Actions → General → Workflow permissions` et, uniquement si nécessaire, configurer un jeton GitHub aux permissions minimales dans le secret `KOVELYX_RELEASE_TOKEN`. Ne jamais publier ce secret dans le dépôt ou le chat.

Un échec peut aussi indiquer qu’aucun APK de la plateforme visée n’a été compilé lors du commit : les workflows ont été ajustés pour distinguer ce cas d’un échec réel de l’application.

## Signature Android et mises à jour

Les APK actuels utilisent une **signature Debug de test**, susceptible de changer entre compilations GitHub Actions. Même si l'APK s'installe, une mise à jour par-dessus une autre préversion peut donc échouer avec une signature incompatible. **Sauvegarder les données et ne pas promettre de mises à jour Android transparentes** jusqu'à la configuration d'une clé de signature Kovelyx stable et privée.

Pour une Release Android de production, il faudra une signature stable, une version de paquet correctement incrémentée et une série de tests fonctionnels sur téléphone et Android TV si TV doit être officiellement pris en charge.

## État de l'indépendance

Le dépôt, les builds, l'identité et les sources sont chez Kovelyx ; l'audit des dépendances héritées de Nuvio est encore en cours. Les notices de licences et attributions doivent être conservées.
