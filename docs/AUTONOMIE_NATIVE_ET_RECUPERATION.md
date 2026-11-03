# Autonomie native et récupération — 3 novembre 2026

## Changements

- Guide parental retiré des réglages, des affichages et des clients réseau Windows/mobile/TV. Le service externe n'est plus appelé. Les anciens champs de préférences et certains DTO sont conservés pour la compatibilité des données ; une ancienne préférence activée ne réactive plus le service.
- Les frameworks Apple ne sont plus téléchargés depuis Nuvio. La préparation Apple réclame explicitement des frameworks locaux. Apple reste hors distribution Windows/Android/TV : ce changement ne certifie pas un build Apple fonctionnel.
- Libtorrent : les 11 fichiers concernés doivent correspondre aux SHA-256 des sources épinglées ou de l'intégralité du résultat patché. Les huit patches sont appliqués dans une copie temporaire puis leurs résultats vérifiés. Les fins de ligne CRLF Windows sont normalisées pour les patches. Un cache partiellement modifié est refusé, au lieu d'être accepté sur la présence d'une simple chaîne.
- La reconstruction Windows P2P et Android quatre ABI est déclenchée sur les modifications du moteur. Elle produit des candidats, pas un remplacement automatique des bibliothèques utilisées par les applications.
- Archive publique de récupération `native-vault-v1` : copie du commit source avec tous ses fichiers suivis, bibliothèques précompilées incluses, plus MPV Windows récupéré depuis Kovelyx et vérifié. Chaque fichier est relu et contrôlé ; les vrais contenus Git LFS sont vérifiés contre leurs empreintes, et non confondus avec leurs fichiers pointeurs. Cette archive est exclue du nettoyage des trois dernières versions applicatives et n'est pas la dernière version applicative.
- MPV peut être restauré depuis cette archive durable ou depuis les ZIP portables Kovelyx. L'empreinte et la taille attendues restent imposées.
- La dépendance AndroidX Trace utilisée par le lanceur de tests Android est conservée dans la variante optimisée pour permettre les essais du vrai APK.

## Ce que ces changements ne prouvent pas

Conserver un binaire permet de continuer à le distribuer sans le dépôt d'origine. Cela ne prouve pas qu'il a été reconstruit depuis les sources exactes. La réussite d'une reconstruction du moteur n'est pas une preuve pour MPV, QuickJS, libdovi ou Nextlib.

| Composant | Conservation | Reconstruction exacte de l'ancien binaire |
|---|---|---|
| Moteur P2P | Sources et binaires archivés | Candidat complet Windows/Android à valider en CI puis en lecture réelle avant remplacement |
| MPV Windows | DLL vérifiée dans l'archive durable, SHA-256 et taille imposés | Non certifiée : le binaire historique n'indique pas à lui seul tous ses patches/options |
| QuickJS Android modifié | AAR existant archivé | Sources exactes du fork historique à établir |
| Media3 et FFmpeg | AARs, sources et patches présents selon composant | Toute la chaîne de génération des AAR historiques n'est pas certifiée |
| Nextlib MediaInfo TV | AAR local archivé | Source et recette exactes de cette variante statique à établir |
| libdovi TV | Bibliothèques statiques par ABI archivées | Correspondance exacte source/outillage à établir |

L'archive publique n'inclut aucune base D1 privée, clé de signature, secret de compte ni clé de récupération. Elle ne remplace donc pas une sauvegarde privée des comptes. La fusion de la branche de travail vers main doit préserver le commit réellement testé ; vérifier le statut des contrôles et ne pas masquer un échec de reconstruction native derrière les tests unitaires de l'application.

## Vérifications

Tests locaux : application et réapplication des huit patches sur les entrées du commit libtorrent épinglé, variantes LF et CRLF, refus d'une source altérée ; analyse des connexions Nuvio codées en dur ; syntaxe JavaScript du lecteur Windows ; XML des traductions modifiées ; tests de rétention protégeant l'archive native.

Les résultats des constructions et tests d'applications sont visibles dans GitHub Actions. Un workflow simplement ajouté ou déclenché n'est pas un workflow réussi.

## Sauvegarde privée D1

Le workflow chiffre les exports et les conserve 90 jours ; le calendrier quotidien à 03:40 UTC devient actif sur la branche principale. Le contrôle de restauration reconstruit chaque image/GIF et chaque catalogue publié pour vérifier taille et SHA-256, y compris les caractères Unicode entre blocs. Une validation échouée laisse la copie brute chiffrée disponible, avec un statut explicite d’échec ; elle ne la présente pas comme valide. Le bundle de signature Android est également chiffré avec le même certificat de récupération lorsqu’il est configuré dans GitHub ; le manifeste précise sa présence. Le secret Better Auth existant ne peut pas être exporté depuis Cloudflare et n’est pas présenté comme sauvegardé.
