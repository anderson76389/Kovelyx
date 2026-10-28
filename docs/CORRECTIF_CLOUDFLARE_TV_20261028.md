# Correctif Cloudflare et retrait des anciens rapports

Branche : `fix/cloudflare-network-status-and-settings`.
Base : `ffee04a13aab160ce7db48d4fc1e61d178e75060`.

## Comportement corrigé

- Mobile et Windows : vérifier le Worker configuré avec `/ready` avant les sondes publiques, sans tester l'ancien Supabase en mode Cloudflare. Un HTTP en erreur du Worker reste un échec. En mode invité sans serveur configuré, ne pas afficher de panne de serveur inexistante.
- Mobile et Windows : envoyer les demandes de synchronisation complète à la file Cloudflare existante, au lieu de quitter immédiatement.
- TV : appliquer les mêmes en-têtes JSON et client aux requêtes Cloudflare et une origine du Worker aux mutations, y compris les écritures de synchronisation.
- TV : ne plus instancier le ViewModel SIMKL depuis l'écran de suivi ni ouvrir son parcours de connexion. Retirer SIMKL du sélecteur de recommandations.
- Trois applications : convertir les anciennes préférences SIMKL chargées vers la bibliothèque locale, la progression Kovelyx et les recommandations TMDB. Conserver les choix Trakt et MDBList existants.
- Mobile et Windows : désactiver et masquer les rapports Sentry même si une ancienne préférence était active.
- TV : retirer le démarrage Sentry et son réglage, et désactiver les préférences historiques de rapports de plantage et de lecture. Les statistiques locales du lecteur restent disponibles.

Les SDK et certaines classes historiques restent présents ; leur suppression complète n'est pas annoncée par ce correctif. Le backend Cloudflare n'est pas modifié et aucune migration D1 n'est requise.

## Compilations préparées

Les workflows `build.yml`, `windows.yml` et `functional-verification.yml` acceptent les push sur cette branche. Ils produisent les artefacts de compilation et rapports habituels. Les publications automatiques de production restent limitées à `main`.

Après envoi de la branche, contrôler d'abord le job APK TV et les tests TV, puis le job APK mobile et le packaging Windows. Une compilation seule ne valide pas la synchronisation entre appareils. Conserver la clé Android permanente existante pour les versions distribuées.

## Validation réalisée

- `git diff --check` : réussi.
- Quatre contrôles statiques des frontières Cloudflare/anciens services : réussis.
- Audit statique général : 2 663 sources et fichiers de build inspectés, aucune URL de service historique interdite détectée.
- Audit statique TV : 920 sources inspectées, aucune URL Nuvio/NuvioMedia hors crédits et licences.
- Audit statique mobile : 864 sources inspectées, aucune référence réseau interdite.
- Syntaxe YAML et déclenchement de la branche : vérifiés pour les trois workflows modifiés.
- Worker public `/ready` : HTTP 200 avec le schéma `0002_watched_history`, avec les User-Agent `okhttp/4.12.0` et `Kovelyx-Client`.

Les tests Kotlin de migration SIMKL et les suites TV/mobile ont maintenant passé en CI. Les résultats détaillés sont ci-dessous.

## Vérifications sur appareils encore nécessaires

1. TV : connexion, fermeture et relancement, puis vérification du même compte et du même profil.
2. Ajouter un favori et une progression sur TV, les retrouver sur mobile et Windows, puis vérifier le sens inverse.
3. Modifier une donnée hors ligne, rétablir le réseau et vérifier la reprise sans perte.
4. Vérifier que les réglages de rapports et les parcours SIMKL retirés ne réapparaissent pas après mise à jour d'une ancienne installation.
5. Trakt et MDBList : vérifier les identifiants Kovelyx configurés et effectuer une véritable autorisation sur leurs services. Le correctif ne crée pas d'application OAuth ni de faux état connecté.

L'indépendance des binaires natifs et le trafic réseau réel ne sont pas certifiés par les audits statiques. Aucun compte de production n'a été créé ou modifié pour ces vérifications.


## Résultats CI après correction des échecs de publication

- Tests TV et mobile : [workflow 40638832785](https://github.com/anderson76389/Kovelyx/actions/runs/40638832785), réussi sur `e525ecd814be70bd107d960ad3e9901e6fe005b3`.
- Windows : [workflow 40638832809](https://github.com/anderson76389/Kovelyx/actions/runs/40638832809), réussi, MSI/EXE/portable enregistrés dans `Kovelyx-Windows-installers-experimental` (artefact `12795415211`). Version `1.1.741-alpha`.
- Android mobile et TV : [workflow 40640085191](https://github.com/anderson76389/Kovelyx/actions/runs/40640085191), deux builds réussis sur `5de2fa9bbc8fa3ff3a31bd6193ebe84a9a1dcd89`. Version `1.1.742-alpha`.
- TV : les cinq APK, dont l'universel, sont signés et vérifiés ; artefact `kovelyx-tv-debug` (`12795332441`). Choisir le fichier dont le nom contient `universal` pour une installation TV générale.
- Mobile : artefact signé `kovelyx-mobile-debug` (`12795272135`).
- Tests du parcours de signature avec SDK et clé simulés : [workflow 40640085269](https://github.com/anderson76389/Kovelyx/actions/runs/40640085269), réussi (syntaxe, APK unique mobile, cinq APK TV, absence d'APK).

Les fichiers sont des préversions de cette branche. Le code Windows est identique entre les deux commits de build ; le dernier commit ne change que le script de signature Android et son contrôle automatique.

Causes des premiers workflows rouges :

1. Une apostrophe dans une expansion de paramètre Bash empêchait le script de signature de démarrer.
2. Le même script exigeait un seul APK, alors que la TV en produit cinq. Tous les fichiers sont maintenant signés et vérifiés avant leur enregistrement.
3. Un test mobile attendait encore l'absence de nouvelle recherche lors du changement de canal. Il vérifie désormais que l'ancienne proposition disparaît pendant la recherche et que la nouvelle réponse correspond au nouveau canal.
4. Le test Windows déclenchait un avertissement de cache de configuration incompatible avec le pont natif ; la commande de test désactive ce cache. Le délai dépassé du test QuickJS ne s'est pas reproduit lors du passage suivant. Aucune correction du moteur QuickJS n'est revendiquée.

Les signatures utilisent la clé permanente déjà présente dans les secrets GitHub. Aucun déploiement Cloudflare ni changement de données de compte n'a été effectué. La synchronisation entre appareils physiques et les véritables autorisations Trakt/MDBList restent à vérifier.
