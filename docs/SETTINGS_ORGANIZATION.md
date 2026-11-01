# Organisation des paramètres Kovelyx

## PC et Android mobile

- **Compte et profils** : compte Kovelyx et changement de profil.
- **Interface** : apparence et notifications. Le noir AMOLED reste avec le thème ; les réglages d’interface et de navigation portent un titre explicite.
- **Contenu et sources** : extensions, serveurs multimédias, modules complémentaires et historique de recherche. La gestion des sources vient avant les préférences de recherche.
- **Lecture** : commandes du lecteur, langues, sous-titres, sélection des liens, épisode suivant et passage des segments. Les options techniques du moteur, de compatibilité, de tampon/cache et P2P viennent ensuite. Sur Windows, le panneau vide réservé aux moteurs Android est masqué ; RTX conserve son panneau dédié.
- **Services et suivi** : services vidéo/débridage, enrichissement des fiches et notes, puis suivi Trakt/MDBList. Les identifiants de notes et les connexions de suivi restent des réglages distincts.
- **Avancé** : démarrage, fréquences minimale/maximale par appareil, rendu Windows si disponible, maintenance du cache et diagnostics pris en charge.
- **À propos** : mises à jour, puis informations du projet et licences.

Le titre « Présentation des liens vidéo » désigne les badges et l’apparence des liens, et reste dans Apparence. Il ne désigne pas les options de choix automatique d’un stream.

## Android TV

Le rail et les catégories restent conservés. Les sections de lecture sont ordonnées par usage (lecteur, interface, image, audio, sous-titres, choix de source, épisode suivant, segments), puis réseau/tampon et P2P. Les services vidéo, les fiches/notes et les services de lecture ont des sous-titres séparés dans leur écran commun.

Dans Avancé, les cartes gardent leurs clés : mode d’expérience, démarrage, fréquence d’affichage, performance/navigation, cache, diagnostics de lecture/Dolby Vision, puis tests réseau et leurs résultats. Les demandes de focus initial restent sur les mêmes actions ; les nouveaux sous-titres ne sont pas des contrôles sélectionnables.

## Compatibilité

Cette modification porte sur la présentation. Les dépôts de préférences, clés de stockage, réglages enregistrés, connexions, moteurs et services de synchronisation ne sont pas modifiés. Les identifiants d’écrans sont conservés, notamment `TraktAuthentication`, même si ce parcours est présenté comme Suivi et classé avec les services.

Les résultats de recherche suivent les nouveaux titres et la bonne catégorie. Les bornes de fréquence d’affichage sont indexées ; l’ancienne entrée SIMKL absente des écrans actifs est retirée. Les options de décodeur ExoPlayer masquées sur PC ne sont plus proposées dans sa recherche.

Vérifications : comparaison des liaisons de boutons, valeurs et activations avant/après déplacement ; tests de compatibilité des routes sauvegardées et de retour vers les parents ; tests existants des sections et de la visibilité TV ; contrôle du français ; compilations et essais de lecture des trois applications.
