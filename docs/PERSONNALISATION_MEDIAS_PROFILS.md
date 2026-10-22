# Kovelyx — photos, GIF et vidéos de profil

> Branche PR #40, préversion. Aucun média n'a été envoyé sur la base réelle depuis cet assistant.

## Importation privée sur Windows et Android mobile

Dans **Modifier le profil**, choisir « Importer une photo, un GIF ou une vidéo » pour l'avatar et « Importer un fond » pour l'arrière-plan. Sélecteur natif Android ou Windows ; la copie originale est préservée dans le dossier privé de l'application. L'utilisateur peut retirer le média sans effacer le fichier source. L'ancien catalogue d'avatars reste disponible.

Formats reconnus d'après **l'en-tête réel du fichier** : JPG, PNG, GIF animés, WebP, MP4 et WebM ; Windows peut aussi importer BMP et MOV. Les vidéos Android/TV dépendent des codecs réellement disponibles sur l'appareil, surtout WebM/MOV. **Maximum 20 Mo par média.** Les archives, .exe, documents et fichiers non décodables ne sont pas acceptés. « Tout fichier » ne peut pas signifier lire n'importe quel format binaire.

- Les GIF sont copiés tels quels : plus de conversion en seule image fixe sur PC. Sur Android mobile l'application possède déjà Coil avec décodeurs GIF.
- Les fonds vidéo sont lus en boucle, silencieusement : moteur multimédia local embarqué sur PC ; VideoView natif Android et Android TV.
- Les avatars vidéo ont une prévisualisation dans l'éditeur mobile/PC ; sur la TV, ils peuvent jouer dans le cercle de profil. Certains autres endroits utilisant de simples widgets d'image peuvent encore afficher une vignette ou ne pas afficher la vidéo : **à valider**.
- Pas d'abonnement Nuvio pour une image/vidéo importée par l'utilisateur.
- Un fond ou avatar local n'est **jamais automatiquement converti en URL publique**.

## Synchronisation vers la TV : option Cloudflare R2

Le Worker D1 existant garde les UUID de profils et désormais un endpoint privé **GET/PUT /v1/media**. Chaque requête requiert la session Better Auth et vérifie l'appartenance du profil à l'utilisateur. Les objets sont dans R2 sous une clé séparée par utilisateur/profil/type. La TV lit les profils D1, télécharge les originaux avec son jeton en mémoire dans son dossier privé et affiche les médias lorsque le profil TV correspond sans ambiguïté. Il n'y a pas d'URL R2 publique, ni de clé secrète dans le dépôt.

**Cette étape n'est pas active dans le Worker actuel : aucune liaison R2 n'est encore configurée.** Sans elle, les imports continuent à fonctionner localement, mais les autres appareils ne les verront pas automatiquement. Aucun déploiement Worker, migration D1 ni upload de vraie photo n'a eu lieu.

### Activation R2 optionnelle, uniquement après vérification des coûts

1. Vérifier dans Cloudflare Dashboard si **R2 peut être activé** sur le compte et si une méthode de paiement est exigée. **Ne pas activer ni accepter un paiement sans le vouloir**. Les quotas gratuits et les règles de facturation peuvent évoluer. R2 n'est pas « gratuit pour toujours ».
2. Dans le Codespace connecté à Cloudflare, vérifier le compte avec `npx wrangler whoami`, puis créer (si souhaité) le bucket privé `kovelyx-profile-media` via `npx wrangler r2 bucket create kovelyx-profile-media`. Cette commande peut échouer si R2 n'est pas activé.
3. Décommenter les trois lignes `[[r2_buckets]]`, `binding = "MEDIA"`, `bucket_name = "kovelyx-profile-media"` dans `services/kovelyx-cloudflare/wrangler.toml`, **en conservant le vrai ID D1 existant sur le Codespace**.
4. Seulement après sauvegarde D1, revue des tests de la PR, et confirmation de R2, publier avec `npx wrangler deploy`. Ce déploiement ne doit pas créer une deuxième base ni écraser le secret Better Auth existant.
5. Compiler et tester le même compte sur un Windows + Android + Android TV. Importer un GIF et une MP4 ; vérifier lecture, reconnexion, choix de profils, format non supporté, mode hors-ligne et refus d'accès par un autre compte.

### Limitations à vérifier avant toute Release

- R2 est facultatif et potentiellement soumis à activation/facturation. Sans bucket, **pas de synchronisation d'images/vidéos entre appareils**.
- La première connexion TV n'associe automatiquement un média que lorsque le nom du profil est unique, ou lorsqu'il n'existe qu'un seul profil local et distant ; sinon aucune association dangereuse par numéro local.
- L'échec d'un upload n'efface pas le fichier sur l'appareil. La synchronisation R2 n'est pas dotée d'un tableau de reprises/rejets complet.
- Les vidéos restent limitées à 20 Mo, sans transcodage automatique et sans garantie de compatibilité de tous les codecs.
- Tester l'affichage des vidéos dans tous les endroits de l'UI, pas seulement l'éditeur et les écrans de profil.
- Les sessions Cloudflare des clients actuels restent en mémoire et les mises à jour live ne sont pas encore testées sur trois appareils.
