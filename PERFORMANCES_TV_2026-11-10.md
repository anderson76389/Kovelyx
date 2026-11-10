# Performances TV et fiabilisation des mises à jour — 10 novembre 2026

La [PR #108](https://github.com/anderson76389/Kovelyx/pull/108) a été fusionnée dans main en `aed56108521a585be45281b9894b2b7beae2c277` après réussite des 15 workflows de la révision `730f7cd971ae2e2a33da862e9e562933354e33ac`.

## Mesures avant/après

Un même émulateur Google TV API 34, 2 Gio de RAM et deux cœurs. Trois répétitions par version, ordre baseline/candidate/candidate/baseline/baseline/candidate. Installation propre isolée pour chaque mesure, mode invité, trois lancements à froid, CPU échantillonné au repos et pendant 32 événements DPAD, mémoire PSS après navigation et statistiques d’affichage. Ces réinstallations concernent uniquement le benchmark sur émulateur ; les tests de mise à jour en place sont distincts.

Baseline : code applicatif main `83c81a84cce6630ae6c69e042751298e5416b163`, build [42835163700](https://github.com/anderson76389/Kovelyx/actions/runs/42835163700). Le script vérifie que les sources TV sont identiques à celles de la base de la PR. Candidate : build [42837728813](https://github.com/anderson76389/Kovelyx/actions/runs/42837728813), SHA de la PR ci-dessus. Même variante APK optimisée, identité vérifiée et signatures APK valides ; aucune clé de publication utilisée pour installer ces APK dans le benchmark.

| Mesure médiane | Baseline | Candidate | Interprétation |
|---|---:|---:|---|
| Lancement à froid | 883 ms | 866 ms | Petit écart ; plages recouvrantes, aucun gain général démontré |
| PSS après navigation | 67 807 Kio (66,2 Mio) | 66 566 Kio (65,0 Mio) | Petit écart ; trois répétitions ne suffisent pas à prouver une réduction globale |
| CPU au repos | 0 % | 0 % | Médiane des intervalles échantillonnés, pas une garantie d’inactivité permanente |
| CPU en navigation | 15 % | 15 % | Aucun gain de CPU observé sur ce scénario |
| Taille de l’APK | 133 753 569 octets | 133 668 668 octets | Différence de 84 901 octets, trop faible pour revendiquer une optimisation importante du stockage |

Aucune régression au-delà des seuils du test : démarrage +30 % et au moins 200 ms ; PSS +25 % et au moins 32 Mio ; CPU +30 % et au moins 5 points. Ces seuils détectent des dégradations importantes, sans exclure des régressions plus petites.

| Passage | Version | Démarrage ms | PSS Kio | CPU repos % | CPU navigation % | Images | Images lentes |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | baseline | 907 | 68079 | 0 | 15 | 72 | 54 |
| 2 | candidate | 866 | 67048 | 0 | 15 | 72 | 53 |
| 3 | candidate | 829 | 66566 | 0 | 14 | 71 | 53 |
| 4 | baseline | 883 | 67807 | 0 | 13 | 69 | 63 |
| 5 | baseline | 836 | 66862 | 0 | 15 | 70 | 52 |
| 6 | candidate | 889 | 65618 | 0 | 15 | 72 | 54 |

Le nombre d’images classées lentes est élevé dans les deux versions sur cet émulateur. Les résultats sont comparables et ne démontrent pas une amélioration de fluidité. L’émulateur, les animations désactivées et ce court parcours invité ne représentent pas une TV physique.

Mesures complètes : [JSON](TV_PERFORMANCE_2026-11-10.json). [Workflow et artefact avec mesures brutes et provenance](https://github.com/anderson76389/Kovelyx/actions/runs/42837729850).

## Optimisations et protections intégrées

- Historique des revalidations d’affiches borné à 256 entrées au lieu d’une table sans limite ; test Kotlin de 10 000 affiches distinctes. Au plus 8 revalidations réseau réellement simultanées, avec cooldown et affichage immédiat du cache préservés.
- Lectures OTA de 64 Kio et progression plafonnée à environ 100 notifications. Pour le fichier TV de 133 753 569 octets, le calcul déterministe donne 16 328 notifications → 98, et 16 328 lectures → 2 041. Cela ne mesure ni la batterie ni le CPU d’une vraie mise à jour.
- Ancien installateur Windows conservé jusqu’à vérification du nouveau SHA-256. Nettoyage des anciens APK uniquement après validation complète du nouvel APK, dans le dossier de cache dédié.
- Refus de l’installation si la lecture de l’autorisation Android échoue ; annulation du téléchargement TV respectée.
- Vérification de continuité du certificat avec les APK publics précédents avant toute publication de nouvelles APK signées.

## Validation fonctionnelle

Les 15 workflows de la PR finale sont réussis : compilations mobile/TV, MSI/EXE/portable Windows, tests Kotlin mobile/TV et Desktop, régressions Android après synchronisation, contrats des cinq paquets de mise à jour, indépendance et confidentialité, lecture H264 ExoPlayer/libmpv mobile et Media3 TV, parcours Google TV et comparaison avant/après.

Les tests [mobile](https://github.com/anderson76389/Kovelyx/actions/runs/42837729127) et [TV](https://github.com/anderson76389/Kovelyx/actions/runs/42837728837) vérifient le remplacement en place sans désinstallation, la conservation de la date d’installation initiale et le redémarrage. Une signature temporaire commune isole ce test mécanique dans la CI ; la continuité du certificat public est vérifiée séparément par la publication de confiance.

Limites : aucune mesure sur les appareils physiques de l’utilisateur, aucune mesure CPU/PSS pendant une vraie lecture 4K avec extensions, aucun compte Cloudflare réel utilisé par le benchmark et aucune preuve de conservation de tous les profils réels tirée du seul test de remplacement.

## Endurance TV sur la révision finale

Le parcours Google TV a exercé 96 cycles, 4 retours à l’accueil et 2 veilles/reprises en 170,8 secondes. Le PID est resté identique, sans ANR/crash détecté. PSS initiale 80 908 Kio (79,0 Mio), pic échantillonné 84 059 Kio (82,1 Mio), finale 78 153 Kio (76,3 Mio). La variation après échauffement est de −3 916 Kio et le seuil d’alerte de croissance n’a pas été atteint. Ces 171 secondes ne prouvent pas l’absence de fuite pendant plusieurs heures ou en lecture 4K.

[Données d’endurance conservées](TV_ENDURANCE_2026-11-10.json) et [workflow source](https://github.com/anderson76389/Kovelyx/actions/runs/42837728837).

## Tri des anciennes PR

Huit anciennes propositions remplacées ont été fermées, leurs branches et commits conservés. Sept autres propositions anciennes restent ouvertes faute de preuve d’équivalence complète ou parce qu’elles portent un travail distinct. [Décisions et preuves par PR](PR_TRIAGE_2026-11-10.md).
