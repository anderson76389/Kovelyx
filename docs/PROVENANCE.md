# Provenance et commits épinglés

Kovelyx importe des **instantanés** (arbres de fichiers, sans historique Git complet) de projets publics. Les branches d'origine ne sont pas suivies après import.

| Dossier | Origine | Commit d'origine |
|---|---|---|
| `apps/tv` | https://github.com/NuvioMedia/NuvioTV | `fe3142bf5c8357e59e0ecb274c54eb5ccb284b00` |
| `apps/mobile` | https://github.com/NuvioMedia/NuvioMobile | `6d435785c0e082ae3d4fbb8152e1ff5e45c2e575` |
| `apps/desktop` | https://github.com/NuvioMedia/NuvioDesktop | `51963ee4d83057ef08836f4ed9ab68037e081dd3` |
| `server` | https://github.com/NuvioMedia/self-host | `39ea2bd1bc71636127f9d797599c23b4236960c4` |
| `apps/mobile/MPVKit` | https://github.com/NuvioMedia/MPVKit | `bfeb6fcfa2bd5081a9666ae5bb42b50e6c1537e8` |
| `apps/desktop/MPVKit` | https://github.com/NuvioMedia/MPVKit | `bb1d0250ddcfa9d220761fcdad6011ac9fecf15e` |

Les imports préservent les licences et notices des archives. Ils **ne copient pas** l'historique complet des projets en amont.

## Dépendances Git non résolues

Les arbres des commits d'origine signalent des `gitlink` sans URL exploitable dans `.gitmodules` :

- TV : `libmpv-android` → `f48aa82b2677428f756a689a44c2477c3f37be51`.
- Mobile + Desktop : `libass-android` → `c10b71ab8b4d90796d4a795f775d337c29198ad0`.

**Ces bibliothèques ne sont pas intégrées par l'import initial.** Il faut identifier leurs sources, licences, et modes de compilation, ou choisir des substituts valides. Ne pas annoncer une compilation validée tant que ce point n'a pas été traité.

## Objets Git LFS

L'archive Git peut transporter des *pointeurs LFS* plutôt que le contenu binaire : c'est notamment pertinent pour les bibliothèques natives MPV de la version Desktop. Un pointeur LFS ne représente **pas** une sauvegarde autonome du fichier. Les objets requis devront être transférés vers un stockage contrôlé par Kovelyx, puis leur intégrité vérifiée.

## Hébergement

Le dépôt `self-host` fournit une configuration Docker et des scripts, mais utilise des images de conteneurs tierces, y compris `ghcr.io/nuviomedia/nuvio-account-dashboard:latest`. La disponibilité à long terme du serveur **n'est pas garantie** par la seule copie de ce dépôt.

## Marques et licences

- NuvioTV, NuvioMobile et NuvioDesktop : **GPL-3.0** ; les sources dérivées distribuées doivent respecter les obligations GPL et notices.
- self-host : **Apache-2.0** ; conserver notamment `LICENSE` et `NOTICE`.
- MPVKit : conserver et respecter sa licence présente dans les sources.
- Revoir séparément les licences de bibliothèques, codecs, images, icônes, contenus et marques avant distribution publique.
