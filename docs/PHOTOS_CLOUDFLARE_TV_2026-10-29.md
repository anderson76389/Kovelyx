# Images privées Cloudflare, synchronisation et charge TV — 29 octobre 2026

## Cause constatée

Le Worker déployé répondait `200` sur `/health` et `/ready`, avec le schéma `0002_watched_history`. La synchronisation bibliothèque/progression/historique est distincte des photos. Les sélecteurs copiaient correctement les photos personnelles dans le stockage local, mais le client Cloudflare ne transférait aucun fichier et le Worker ne proposait aucune route d’image. Les champs de profil n’attachaient que les noms/UUID. Le transport HTTP Windows ne préservait pas les octets binaires bruts.

## Transfert des photos et fonds

- Migration additive `0003_profile_media.sql` : deux tables privées, liées au propriétaire et à l’UUID du profil, suppression en cascade. Aucun compte ni historique existant n’est supprimé par la migration.
- Route authentifiée `/v1/profile-media` : téléversement, récupération et suppression ; contrôle du propriétaire, version attendue pour les écritures, empreinte SHA-256 et opérations D1 atomiques. Aucun lien public, chemin local, jeton dans une URL, stockage Nuvio ou R2 supplémentaire.
- Fichiers de 2 Mio maximum, répartis en cellules D1 de 512 Kio. JPG/PNG/WebP/GIF uniquement. Les images fixes sont préparées côté appareil (avatar 512 px, fond 1 920 px maximum) et les GIF acceptés restent inchangés.
- Les versions sont attachées au profil. Une image inchangée est relue depuis un fichier privé local, sans nouveau transfert des octets. Le cache ne conserve qu’une version par propriétaire/profil/type.
- Les modifications explicites sont conservées localement tant que le transfert n’est pas confirmé. Un autre compte/profil possède une entrée et un cache séparés. Une nouvelle modification pendant le transfert reste en attente. Une empreinte incorrecte ne remplace pas l’image locale.
- Windows, Android mobile et TV utilisent le même algorithme de transfert. Les sources Apple hors des trois distributions actuelles gardent un adaptateur inactif ; aucune quatrième version fonctionnelle n’est annoncée.

## Réduction du travail inutile sur TV

La progression TV n’attend plus indéfiniment une pause des événements du lecteur : elle est regroupée sur une fenêtre de dix secondes. Les instantanés complets ne sont plus hachés à chaque événement. Les écritures Cloudflare ne passent plus par les RPC historiques dans les parcours de progression/suppression corrigés. Les mutations envoyées sont acquittées pour éviter d’accumuler inutilement des opérations locales. Les suppressions sont appliquées avant de fusionner le vieux cliché distant. Les clients interrogent les changements distants toutes les minutes au premier plan.

Windows/mobile conservent aussi leurs suppressions Cloudflare dans une file durable, par propriétaire et UUID de profil. Le traitement des clés de bibliothèque utilise un ensemble au lieu d’un parcours répété des mutations pour chaque entrée.

Les réglages TV déjà présents (cache d’images limité, décodages parallèles limités, chargements d’images bornés sur appareils à faible mémoire) sont conservés. Aucun gain de fréquence d’images n’est annoncé sans mesure sur matériel réel.

## Indépendance vis-à-vis de Nuvio

Le code, les bibliothèques livrées, les mises à jour et le Worker des trois distributions sont conservés dans Kovelyx. Les scans actifs ne trouvent aucune ancienne URL de service autorisée ; la référence de refus explicite du serveur historique reste volontairement dans Desktop. Les packages/symboles `com.nuvio` et les licences attestent de l’origine du code, sans constituer une connexion. Les sources Apple encore liées à des téléchargements NuvioMedia ne sont pas utilisées par ces trois paquets.

Cela ne prouve ni l’absence d’une URL calculée dans toutes les bibliothèques ni le fonctionnement de tous les formats de lecture. La disparition des serveurs Nuvio n’est pas un motif de suppression des bibliothèques natives déjà incluses. Les services tiers légitimes et les identifiants OAuth du propriétaire restent nécessaires pour leurs fonctions.

## Déploiement et vérification

Le transfert d’images exige **le nouveau Worker et la migration D1 0003**. Une compilation d’APK seule ne met pas ce serveur à jour. Avant toute migration, utiliser la sauvegarde D1 exportée et vérifiée par `services/kovelyx-cloudflare/scripts/setup.mjs`, puis conserver cette sauvegarde hors du runner et du dépôt. Le script conserve les comptes et secrets existants.

Depuis un Codespace du propriétaire déjà connecté à Cloudflare : récupérer cette branche, puis exécuter `npm install` et `node scripts/setup.mjs` dans `services/kovelyx-cloudflare`. Le résultat `/ready` doit être `status: ok`, `schema: 0003_profile_media`. Le workflow de déploiement existant requiert les accès Cloudflare et une sauvegarde confirmée ; les tests n’exécutent aucune migration distante.

Tests ajoutés : fichier privé réellement transféré entre deux sessions d’un compte, refus d’un autre propriétaire, limites/segments D1, écritures concurrentes, suppression avec version, cache local, reprise hors ligne, nouvelle modification pendant une requête, intégrité binaire Windows et conservation de deux images d’un GIF. Les tests Worker locaux sur GitHub utilisent des comptes et une D1 jetables. Le lancement local de Wrangler dans l’environnement de travail était bloqué par l’accès aux interfaces réseau ; ce blocage ne prouve pas une panne du serveur.

Les builds APK et Windows neutralisent désormais aussi les cinq domaines Nuvio connus dans leur fichier d’hôtes et redirigent les clones Git NuvioMedia vers un domaine invalide. Ce contrôle ne bloque pas tous les hôtes calculés à l’exécution ni tous les téléchargements HTTP possibles.

Le contrôle GitHub du déploiement a constaté l’absence de `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` et `KOVELYX_D1_DATABASE_ID`. Aucun accès Cloudflare connecté n’a été trouvé dans les outils disponibles. La migration 0003 et le Worker ne sont donc pas annoncés déployés. Les résultats définitifs de compilation et de déploiement sont consignés dans la PR. Un essai sur TV et un échange authentifié des photos sur les appareils du propriétaire restent nécessaires avant d’affirmer leur fonctionnement réel.

## Addons et charge réseau TV

Les dépôts d’addons Windows/mobile et le service TV envoyaient encore leurs changements aux RPC Supabase. Le parcours Cloudflare les ignorait. Ils utilisent désormais la catégorie D1 existante `addons`, clé `installed` : liste ordonnée des URL de manifeste complètes, nom personnel et activation. Les URL configurées restent dans les données privées du compte ; elles ne sont pas publiées dans les logs ajoutés ni dans une Release. Aucun mot de passe de fournisseur n’est extrait ou transféré séparément.

Un checkpoint durable est isolé par propriétaire et UUID de profil. Une liste vide représente aussi une suppression. Les changements locaux depuis le dernier état confirmé sont envoyés avec la révision lue ; une panne ou un conflit laisse la modification en attente. Une réponse ne remplace pas une édition ou un compte changé pendant HTTP. Le choix « addons du profil principal » est résolu vers l’UUID de ce profil. Les clients ayant déjà un état cloud l’importent à leur première connexion ; ensuite les modifications locales sont reconnues même après redémarrage. Ce contrat d’addons ne demande pas de nouvelle migration D1.

Sur TV, quatre demandes de manifeste au maximum partagent désormais le même limiteur, y compris les absences de cache et le rafraîchissement en arrière-plan. Le cache et son délai existant restent utilisés. Un test lance douze demandes et mesure un pic de quatre. Les tests communs couvrent deux appareils, les URL configurées, ordre/activation/noms, suppression de la dernière extension, absence d’envoi en boucle, reprise après panne, changement de compte et refus d’acquitter une édition concurrente. Le test Worker+D1 vérifie aussi le passage entre deux sessions, le conflit de révision et le refus d’un autre compte.
