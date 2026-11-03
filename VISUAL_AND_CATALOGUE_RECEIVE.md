# Réception des collections et préférences visuelles

Le parcours d’import depuis un site sur Windows attend désormais le catalogue Cloudflare natif. Il ne passe plus par un RPC historique incompatible. Les collections restent conservées localement pendant une erreur ; un bouton de synchronisation et un état distinct dans leur gestion montrent le transfert, l’attente, la réussite ou l’échec.

Un point de contrôle cloud ne suffit plus à ignorer une réception : le moteur vérifie également l’empreinte du catalogue réellement présent localement. Une restauration de préférences sans les collections récupère donc la version cloud inchangée. Une suppression volontaire marquée par l’application continue à être publiée. Les formats natifs de Ma-Collection/Kaptain et leurs sources TMDB/Trakt/extensions sont conservés, dans la limite existante de 4 Mo par ensemble. Les fichiers d’affiches/vidéos externes ne sont pas dupliqués.

Les préférences visuelles PC/mobile sont séparées par fonctionnalité (thème, écran méta, affiches, profondeur, présentation des dossiers et affiches personnalisées). Elles utilisent D1, des révisions et un point de contrôle durable ; aucun réglage matériel du lecteur ni identifiant de service n’est ajouté à cette synchronisation. La TV partage les paramètres de thème compatibles ; ses options de mise en page restent propres à son interface. Un réglage absent de l’interface TV n’est pas artificiellement ajouté.

Une réception partiellement interrompue reste une réception à reprendre ; elle ne peut pas être republiée comme modification locale. Les modifications explicites en cours d’envoi restent en attente et une réponse HTTP perdue ne provoque pas de nouvelle écriture inutile.

Mêmes compte/profil, versions mises à jour et connexion active requis. Garder le PC ouvert pendant la première publication d’un catalogue complet ; la réception d’un autre appareil dépend de la fin de cette publication. L’état de réussite concerne le catalogue et sa disposition d’accueil, pas la disponibilité des fournisseurs externes.
