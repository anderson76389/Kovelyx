# Diagnostic du rendu TV et lecture prolongée

## Origine des images lentes du comparatif précédent

Les six dumps `gfxinfo` du [comparatif 42837729850](https://github.com/anderson76389/Kovelyx/actions/runs/42837729850) ont été décomposés à partir des timestamps des images régulières. Les données incomplètes et sentinelles sont rejetées, les doublons de fenêtre ne sont comptés qu’une fois. Le parseur et ses quatre tests sont conservés dans `scripts/tests/tv_frame_phases.py` et `test_tv_frame_phases.py`.

| Phase, médiane par passage | Plage observée sur les six passages |
|---|---:|
| Image totale, de IntendedVsync à FrameCompleted | 34,4–35,5 ms |
| Thread UI, de HandleInputStart à SyncQueued | 2,55–2,74 ms |
| Attente du thread de rendu | 0,68–1,98 ms |
| Soumission du dessin | 1,65–2,35 ms |
| Après SwapBuffers jusqu’à FrameCompleted | 21,1–26,2 ms |

Les phases ne sont pas indépendantes : la médiane de leur somme n’est pas la somme de leurs médianes. La phase après SwapBuffers inclut de la synchronisation et de la présentation ; elle n’est pas une mesure du seul temps d’exécution GPU.

Les journaux du workflow confirment `-gpu swiftshader_indirect` et `SwiftShader Device (Subzero)`. Il s’agit d’un GPU logiciel. Les compteurs indiquent zéro upload de bitmap lent sur cinq passages et un sur le sixième, contre 51 à 63 commandes de dessin lentes par passage. Cela pointe surtout vers le chemin de rendu/présentation de cet environnement ; ce n’est pas une preuve qu’un composant particulier de l’application est responsable sur une TV physique. Les valeurs 4950 ms de l’histogramme GPU ne servent pas de mesure de durée du thread UI.

Les résultats ne justifient pas à eux seuls le retrait d’animations, de flou ou une réécriture du défilement. Aucune modification visuelle n’est introduite sans bénéfice mesuré. [Décomposition complète](TV_FRAME_PHASES_2026-11-10.json).

## Test de lecture de trente minutes

Le scénario Kotlin est conservé dans `scripts/tests/fixtures/tv/Media3PlaybackSoakTest.kt` et copié dans le module Android uniquement dans ce job. Le workflow `tv-playback-soak.yml` génère localement une vidéo H264/AAC 1280×720 à 30 images/s pendant 30 secondes et vérifie son empreinte avant de l’utiliser dans une liste de trois épisodes, répétée pendant une session de 1800 secondes.

Le test utilise le moteur Media3/AAR corrigé et le chemin de contrôle de chargement Kovelyx avec allocateur natif et budget de mémoire par défaut du mode performance. Il exerce six pauses/reprises de cinq secondes, six déplacements à 15 secondes et cinq passages explicites à l’épisode suivant. Des images décodées doivent continuer à être produites entre les relevés.

Chaque minute : PSS totale, PSS native/Java, tas natif, CPU du processus mesuré par `Process.getElapsedCpuTime`, position, épisode et nombre d’images décodées. Les CPU ne comptent pas les éventuels services de décodage externes au processus. La croissance PSS soutenue est comparée après échauffement, avec échec au-delà de 64 Mio ; cela n’exclut pas toute petite fuite. Les relevés sont enregistrés pendant le test, y compris si une assertion échoue.

Ce test est exécuté explicitement par le workflow long ; il n’allonge pas involontairement les suites générales. Le workflow exige un rapport `passed=true`, au moins 1800 secondes et des images réellement décodées : une absence de test ou une simple attente ne compte pas comme succès.

Limites : APK d’instrumentation non minifiée, surface de sortie privée continuellement vidée, vidéo locale 720p et contrôles exercés par API. Cela ne certifie pas les boutons de l’interface du lecteur, une lecture 4K/Dolby Vision, le réseau des extensions ni une TV physique. Aucune donnée utilisateur ni clé de publication n’est employée. Les résultats seront ajoutés après l’exécution réelle ; la présence du test ne vaut pas réussite.
