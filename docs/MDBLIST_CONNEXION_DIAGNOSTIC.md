# MDBList : connexion et catalogues dans Kovelyx

## Deux configurations différentes

1. **Clé API de notes** : connecte-toi à [MDBList → Preferences](https://mdblist.com/preferences/) et copie ta clé dans **Kovelyx → Paramètres → MDBList → Clé API**, puis active les notes. Cela sert à récupérer des notes via l'API et ne constitue pas une connexion OAuth pour l'historique.
2. **Connexion au compte / historique / progression** : dans **Kovelyx → Paramètres → Suivi → Configurer les clés Trakt et MDBList**, renseigne l'**OAuth Client ID MDBList** de **ta propre application Device Code**, créée dans [MDBList Developer](https://mdblist.com/developer/). Ce Client ID est différent de la clé API de notes. Ensuite, dans la carte MDBList, choisis **Connecter** et approuve le code affiché sur le site officiel. Le code n'est pas un mot de passe.
3. Les identifiants Trakt et MDBList sont distincts. Ne copie pas ceux de Nuvio ou d'une autre application. Ne publie ni clé, ni token, ni mot de passe dans GitHub, un ticket ou une capture.
4. Si l'écran MDBList affiche **invalid_client** ou un message d'identifiants manquants, contrôle le Client ID OAuth, pas la clé API. Si l'écran indique un refus du compte, vérifie que tu t'es connecté au site MDBList avec une méthode actuellement offerte par celui-ci (e-mail, Google, etc.) et que le code n'a pas expiré. Ne te base pas sur une ancienne connexion Trakt si MDBList ne la propose plus.
5. Si le site refuse aussi la connexion **hors Kovelyx**, il faut d'abord rétablir ton compte MDBList sur le site officiel. Kovelyx ne peut pas contourner un refus OAuth du fournisseur.

## Pourquoi les catégories restent parfois vides

La synchronisation des collections depuis Ma-Collection transmet les **dossiers et les sources**, **pas les œuvres déjà résolues**. Les résultats dépendent de leur fournisseur :
- `provider: tmdb` : activer/configurer TMDB et vérifier une liste publique simple sur cet appareil ;
- `provider: trakt` : accès Trakt et liste disponible ;
- `provider: addon` : l'extension correspondante doit être installée, activée et capable de retourner le `catalogId` sur cet appareil ; par exemple `aio-metadata` n'est pas automatiquement fourni par MDBList.

Dans **Extensions → Importer un fichier JSON d'extensions** sur Android ou PC, un nouveau sélecteur accepte des liens HTTPS `/manifest.json` ; le JSON de collections va dans **Collections → Importer JSON**. Un ZIP HTML de site n'est pas une extension installable. Sur TV, la configuration des extensions via URL reste la méthode habituelle.

## Diagnostic non destructif

Sur le même compte/profil, ouvre un dossier vide et contrôle sa source : **TMDB, Trakt ou addon**. Teste la même source dans Kovelyx sans modifier la collection. Ne supprime pas le catalogue et ne le remplace pas en masse lorsque l'API renvoie zéro résultat. Une clé MDBList invalide ne prouve pas que la collection elle-même a perdu ses films.
