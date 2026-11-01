# Synchronisation des catalogues Kovelyx

Les comptes Cloudflare utilisent exclusivement le Worker Kovelyx et D1 pour les définitions de collections et leurs réglages d’accueil. Les anciens RPC de collections et d’accueil ne sont pas appelés quand Cloudflare est configuré.

La première rencontre entre deux appareils fusionne les collections, dossiers et sources uniques. Après cette migration, une suppression se transmet aussi : une liste vide n’est plus systématiquement ignorée. Chaque compte et chaque profil possèdent leur état durable distinct. Une modification locale pendant un transfert reste en attente.

Les définitions sont limitées à 4 000 000 octets UTF-8 par ensemble (collections ou accueil), avec au maximum 700 blocs de 6 000 caractères. Le découpage conserve les paires UTF-16 des emoji. Les clients contrôlent le nombre de blocs, les indices, la taille et le SHA-256 avant de remplacer les données locales. Les clés privées structurées (mot de passe, API key, access token…) ne sont pas exportées par ce mécanisme. Les URL d’extensions configurées restent des données privées du compte : elles ne sont pas publiées.

La publication compare la révision du manifeste et vérifie la présence de tous les blocs dans la même instruction SQL. Les anciennes versions du même ensemble sont supprimées après publication en consultant le manifeste courant. Un conflit, un transfert interrompu ou une incompatibilité conserve les données locales et déclenche une reprise.

La lecture normale de progression utilise les catégories indexées sans les blocs de catalogues. Seuls deux petits manifestes l’accompagnent. Un appareil dont le manifeste est inchangé ne relit et ne resérialise pas les définitions. Les blocs sont téléchargés séparément lors d’un changement. Une modification de l’accueil conserve les champs partagés encore inconnus d’une plateforme.

Mesure du modèle Ma-Collection présent dans le dépôt voisin le 1er novembre 2026 : 2 938 634 octets de JSON compact, soit environ 489 blocs. Le nombre réel dépend du JSON conservé par l’application et des caractères. Il s’agit d’un transfert initial ou après modification, pas d’un envoi à chaque progression de lecture. Ni films, ni résultats de listes, ni octets des affiches ne passent par cette synchronisation. Les quotas Cloudflare restent communs aux comptes, images et autres usages ; aucun coût nul universel n’est garanti. Un dépassement de 4 Mo conserve les définitions locales et diffère la synchronisation.

Les services sources (TMDB, Trakt, fournisseurs d’extensions) restent nécessaires pour charger les contenus de leurs listes. Synchroniser les définitions ne copie pas ces services.

Validation : tests du moteur partagés sur les trois cibles, instructions SQLite réelles pour publication/CAS/nettoyage/index, et scénario Worker + D1 local avec deux sessions du même compte et un compte tiers. Les compilations et essais natifs sont exécutés dans GitHub Actions ; les essais d’émulateur ne certifient pas le comportement de chaque téléviseur physique.
