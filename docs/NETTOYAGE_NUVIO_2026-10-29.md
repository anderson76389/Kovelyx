# Nettoyage prudent des trois clients Kovelyx — 29 octobre 2026

## Changements

26 fichiers supprimés (468 123 octets dans l’arbre courant, historique Git conservé) :
- 20 modèles de tickets, modèles de PR et workflows copiés des anciens sous-projets. GitHub utilise les workflows à la racine du monorepo ; aucun appel de ces copies depuis les scripts Kovelyx n’a été trouvé. Les automatisations des bibliothèques tierces vendoriées sont conservées.
- Un espace de travail d’éditeur pointant vers le dossier personnel d’un ancien développeur.
- Trois anciennes images TV sans consommateur : deux logos hérités et une icône de présentation inutilisée.
- Deux copies PNG/ICO historiques de l’icône Windows, sans consommateur. Le générateur ne les recrée plus. Windows utilise les icônes `kovelyx` ; l’icône ICNS encore référencée par le packaging Apple est conservée.

Le logo encore utilisé par le README TV est remplacé par le wordmark Kovelyx centré. Les README des trois clients sont actualisés : lien TV vers les Releases conservées, configuration Cloudflare, certificat des APK publiés, cartes Trakt/MDBList. Les cinq textes français TV affichant encore Nuvio dans le suivi ou le compte sont corrigés, sans renommer leurs clés de ressources.

## Vérifications

- Recherche des chemins/noms des fichiers supprimés dans les sources, scripts, configurations et ressources avant suppression ; aucune référence active trouvée.
- Audit étendu aux sources partagées de la distribution complète et à Windows : 2 698 fichiers actifs/configurations inspectés, zéro ancienne URL de service autorisée. Une URL historique dans Desktop identifie explicitement un serveur à rejeter ; elle ne déclenche pas de connexion.
- Audit TV : 920 sources Kotlin actives, zéro URL Nuvio hors licences/crédits.
- Audit des bibliothèques embarquées : 40 DLL/AAR/JAR et 6 397 composants internes inspectés, zéro domaine Nuvio connu en clair.
- Français TV : 3 144 chaînes, cinq pluriels, XML/clés/paramètres valides.
- Contrôles du centrage des logos et icônes Android réussis.

Les scripts sont reproductibles depuis la racine : `scripts/tests/verify-upstream-independence.py`, `scripts/tests/audit-tv-nuvio-urls.py`, `scripts/tests/audit-packaged-binaries.py`, `scripts/tests/check-tv-french-resources.py` et `scripts/tests/test_centered_brand.py`. Les compilations finales sont contrôlées séparément dans GitHub Actions.

## Éléments conservés volontairement

- Les packages `com.nuvio.*`, bibliothèques locales, symboles JNI/JNA/C et noms de moteur sont utilisés. Un changement massif de noms ne supprimerait aucune connexion et risquerait de casser le lecteur, les imports ou les mises à jour.
- Les anciennes clés de préférences, noms de fichiers importés (`nuvio-collections.json`), données SIMKL à migrer et schémas de compatibilité restent en place.
- Les fichiers Kotlin encore liés aux types de suivi et les profils de compilation ne sont pas déclarés inutiles sur le seul critère du nom. Aucun lecteur ni moteur de suivi nécessaire n’est supprimé.
- Les sources Apple et leurs scripts de préparation sont encore référencés par Xcode/Gradle. Ils contiennent des téléchargements historiques NuvioMedia, mais ne servent pas aux trois paquets Windows/Android/TV actuels. Ils ne sont ni distribués comme une quatrième version indépendante ni remplacés par des URLs inventées. Une future distribution Apple nécessitera leur migration et ses propres essais.
- Licences, notices, attributions et preuves historiques sont conservées.

Ces contrôles sont statiques : ils ne prouvent pas l’absence d’URL calculée, la validité des identifiants OAuth, ni le bon fonctionnement de toutes les lectures et synchronisations sur appareils physiques. Aucun grand dossier n’est supprimé uniquement parce qu’il contient le mot Nuvio.

## Fichiers supprimés

- `apps/desktop/.github/ISSUE_TEMPLATE/bug_report.yml`
- `apps/desktop/.github/ISSUE_TEMPLATE/config.yml`
- `apps/desktop/.github/ISSUE_TEMPLATE/feature_request.yml`
- `apps/desktop/.github/PULL_REQUEST_TEMPLATE.md`
- `apps/mobile/.github/ISSUE_TEMPLATE/bug_report.yml`
- `apps/mobile/.github/ISSUE_TEMPLATE/config.yml`
- `apps/mobile/.github/ISSUE_TEMPLATE/feature_request.yml`
- `apps/mobile/.github/PULL_REQUEST_TEMPLATE.md`
- `apps/tv/.github/ISSUE_TEMPLATE/bug_report.yml`
- `apps/tv/.github/ISSUE_TEMPLATE/config.yml`
- `apps/tv/.github/ISSUE_TEMPLATE/feature_request.yml`
- `apps/tv/.github/PULL_REQUEST_TEMPLATE.md`
- `apps/tv/.github/workflows/android-release.yml`
- `apps/tv/.github/workflows/close-stale-issues.yml`
- `apps/tv/.github/workflows/close-unlabeled-issues.yml`
- `apps/tv/.github/workflows/pr-full-debug-build-comment.yml`
- `apps/tv/.github/workflows/pr-full-debug-build.yml`
- `apps/tv/.github/workflows/pr-template-check.yml`
- `apps/tv/.github/workflows/stale-needs-info.yml`
- `apps/tv/.github/workflows/triage-needs-info.yml`
- `apps/tv/app/src/main/java/com/nuvio/tv/ui/screens/home/NuvioStreaming.code-workspace`
- `apps/tv/app/src/main/res/drawable/nuvio_text.png`
- `apps/tv/assets/nuviotv.png`
- `apps/tv/assets/brand/app_logo_mark.png`
- `apps/desktop/composeApp/src/desktopMain/resources/icons/nuvio-app-icon-transparent.png`
- `apps/desktop/composeApp/src/desktopMain/resources/icons/nuvio-app-icon-transparent.ico`
