# Provenance et commits épinglés

Kovelyx importe des **instantanés** (arbres de fichiers, sans historique Git complet) de projets publics. Les branches d'origine ne sont pas suivies après import.

| Dossier | Origine | Commit d'origine |
|---|---|---|
| `apps/tv` | https://github.com/NuvioMedia/NuvioTV | `fe3142bf5c8357e59e0ecb274c54eb5ccb284b00` |
| `apps/mobile` | https://github.com/NuvioMedia/NuvioMobile | `6d435785c0e082ae3d4fbb8152e1ff5e45c2e575` |
| `apps/desktop` | https://github.com/NuvioMedia/NuvioDesktop | `51963ee4d83057ef08836f4ed9ab68037e081dd3` |
| `apps/mobile/MPVKit` | https://github.com/NuvioMedia/MPVKit | `bfeb6fcfa2bd5081a9666ae5bb42b50e6c1537e8` |
| `apps/desktop/MPVKit` | https://github.com/NuvioMedia/MPVKit | `bb1d0250ddcfa9d220761fcdad6011ac9fecf15e` |

Les imports préservent les licences et notices des archives. Ils **ne copient pas** l'historique complet des projets en amont.

**MPVKit :** les deux commits Gitlink historiques ne possèdent pas de fichier `LICENSE`. Le processus ajoute, à titre documentaire, le texte LGPL-3.0 du commit principal actuel de MPVKit avec une notice d'origine distincte. Son applicabilité aux anciens commits reste à confirmer avant une redistribution de binaires.

## Dépendances Git non résolues

Les arbres des commits d'origine signalent des `gitlink` sans URL exploitable dans `.gitmodules` :

- TV : `libmpv-android` → `f48aa82b2677428f756a689a44c2477c3f37be51`.
- Mobile + Desktop : `libass-android` → `c10b71ab8b4d90796d4a795f775d337c29198ad0`.

**Ces bibliothèques ne sont pas intégrées par l'import initial.** Il faut identifier leurs sources, licences, et modes de compilation, ou choisir des substituts valides. Ne pas annoncer une compilation validée tant que ce point n'a pas été traité.

## Objets Git LFS

L'archive Git peut transporter des *pointeurs LFS* plutôt que le contenu binaire : c'est notamment pertinent pour les bibliothèques natives MPV de la version Desktop. Un pointeur LFS ne représente **pas** une sauvegarde autonome du fichier. Les objets requis devront être transférés vers un stockage contrôlé par Kovelyx, puis leur intégrité vérifiée.

## Infrastructure de comptes actuelle

Les trois applications distribuées (Windows, Android et Android TV) utilisent désormais le backend Kovelyx : Cloudflare Workers, D1 et le système d'authentification propre au projet. Les migrations SQL actives figurent dans [`services/kovelyx-cloudflare/migrations/`](../services/kovelyx-cloudflare/migrations/).

La synchronisation de la bibliothèque et de la progression peut dépendre du service Kovelyx ; un fonctionnement sur appareils réels et une inspection des connexions restent nécessaires avant de certifier tous les parcours. Les applications utilisent aussi des APIs et addons tiers choisis par l'utilisateur.

Les liens vers les dépôts d'origine dans le tableau ci-dessus servent uniquement à la provenance des sources et au respect des licences. Les anciens dépôts et serveurs ne sont pas une condition de compilation des trois applications, d'après [les contrôles de la CI](./CONTROLES_INDEPENDANCE.md).

## Marques et licences

- NuvioTV, NuvioMobile et NuvioDesktop : **GPL-3.0** ; les sources dérivées distribuées doivent respecter les obligations GPL et notices.
- MPVKit : conserver et respecter sa licence présente dans les sources.
- Revoir séparément les licences de bibliothèques, codecs, images, icônes, contenus et marques avant distribution publique.
