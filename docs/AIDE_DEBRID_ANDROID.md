# Aide Kovelyx — Debrid, bibliothèque cloud et liens de lecture

Ce guide concerne **Kovelyx Android** (téléphone et tablette) et remplace l'ancienne FAQ hébergée par Nuvio.

## Deux fonctions différentes

- **Résolution des liens de lecture** : transforme, lorsqu'un fournisseur le permet, un lien ou un contenu distant en URL de lecture. Le résultat dépend du fournisseur, de sa disponibilité, des droits de votre compte et du format du média.
- **Bibliothèque cloud du fournisseur** : affiche ou importe les éléments conservés dans le compte du fournisseur. Une bibliothèque cloud n'est pas nécessairement une liste de films favoris Kovelyx.

Ces fonctions peuvent être proposées séparément selon le fournisseur. Les activer n'accorde pas un abonnement et ne garantit pas qu'un contenu ou une qualité sera disponible.

## Configuration

1. Dans **Paramètres → Debrid**, choisissez le fournisseur proposé dans votre version de Kovelyx (notamment AllDebrid si disponible).
2. Connectez votre compte au fournisseur directement, via la méthode prévue par l'application. Ne publiez jamais une clé API ni un jeton de compte.
3. Activez la résolution des liens si vous souhaitez l'utiliser. Si plusieurs fournisseurs sont configurés, choisissez celui à privilégier.
4. Testez un flux dont vous possédez les droits d'accès.

## Problèmes fréquents

- **Lien de lecture refusé ou expiré :** reconnectez le fournisseur puis réessayez avec un autre flux autorisé. Certains liens sont temporaires.
- **Aucun élément dans la bibliothèque cloud :** vérifiez les éléments effectivement présents sur le site de votre fournisseur et les permissions accordées.
- **Lecture lente / mise en mémoire tampon :** vérifiez le réseau, la taille du fichier, le décodage de l'appareil et les limitations du fournisseur.
- **Téléchargement indisponible :** la disponibilité de la lecture ne prouve pas la prise en charge du téléchargement local.

## Indépendance de Nuvio

Kovelyx utilise ses propres sources et ses propres Releases : https://github.com/anderson76389/Kovelyx/releases

Le **compte Kovelyx** et la **synchronisation de la bibliothèque, de la progression et des œuvres vues** passent par le service Cloudflare Kovelyx lorsqu'il est configuré. **Le compte Debrid est un compte tiers distinct** : ses API et sa disponibilité ne dépendent ni de Cloudflare D1 ni de Nuvio, mais du fournisseur sélectionné.

Pour signaler un dysfonctionnement sans transmettre de secrets : https://github.com/anderson76389/Kovelyx/issues

Conservez vos identifiants, lisez les conditions des fournisseurs et utilisez uniquement des contenus auxquels vous avez légalement accès.
