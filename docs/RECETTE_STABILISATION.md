# Kovelyx — recette finale de stabilisation

> Version de référence au 14 novembre 2026 : **v1.1.1151**, commit
> `d75c6ef122d72125c38ecd51ca44f76839591db3`.
> Ne pas annoncer « 100 % sans bug », « à vie » ou « aucun appel externe » :
> les tests automatisés et les preuves sur appareils réels sont distincts.

## Ce qui a été vérifié automatiquement

- La même révision produit Windows (EXE, MSI, ZIP), Linux x86_64 (AppImage,
  DEB, RPM), Android mobile et Android TV ; SHA-256 attaché à chaque paquet.
- Le pipeline de publication vérifie le certificat des deux APK, le maintien de
  la signature par rapport à la Release stable précédente, et les jobs Windows,
  Linux, français et fonctionnels du même commit.
- Les tests Android mobile/TV, la compilation avec les domaines Nuvio bloqués,
  les contrôles de dépendances et la publication ont réussi pour ce commit.
- Les sauvegardes D1 chiffrées sont programmées et contrôlées. L'archive native
  `native-vault-v1` est conservée séparément.
- Le backend portable Node/SQLite et les tests de coupure réseau sont présents.
  **Une vraie restauration des comptes avec la clé privée reste à faire.**

Le workflow `.github/workflows/stability-watchdog.yml` vérifie une fois par
semaine, sans compilation ni accès aux comptes privés : 8 installateurs et 8
empreintes, provenance du tag, archive native, dernier export chiffré D1,
protection de `main`, anciennes PR, disponibilité et schéma `/ready`.
Les avertissements (branche non protégée, PR anciennes) ne sont pas confondus
avec un échec technique. Il n'efface ni base, ni Release, ni branche.

## Réception sur appareils : critères indispensables

Toutes les vérifications suivantes doivent se faire avec une **installation
existante**, sans désinstaller l'application et sans effacer les données.

| Plateforme | Essais de réception |
| --- | --- |
| HONOR Magic V6 / Android | Mise à jour par bannière sans réinstallation ; identité et connexion ; navigation et lecture ; lecteur externe ; favoris, bibliothèque, ajout/retrait et progression ; relance et permissions. |
| Android TV / Google TV | Mise à jour en place ; démarrage et navigation télécommande/D-pad ; paramètres par couples, mode invité, profils, lecture et reprise ; suppression des addons ; sessions longues, sortie de veille. |
| Windows | MSI/Setup ou installation portable, mise à jour et réouverture ; lecture/lecteur externe ; bibliothèque et profils ; absence de processus persistant non souhaité après fermeture. |
| Linux x86_64 | Test d'AppImage, DEB et RPM **sur distributions adaptées** ; première exécution, lecture, liens, mise à jour et fermeture ; vérifier libmpv/WebKitGTK/codecs système. |

**Scénario commun** : ajouter une œuvre sur mobile, contrôler l'apparition sur
Windows et TV, marquer comme vue, vérifier la progression, supprimer, fermer puis
relancer les applications et confirmer l'état sur les trois appareils. Tester
séparément l'import et le partage volontaire des extensions/catalogues selon le
profil. Les catalogues externes peuvent dépendre de leurs fournisseurs.

**Mesures à relever avant/après 3 mises à jour** : taille réelle du stockage
application + cache + téléchargements, RAM au repos/en lecture, CPU en lecture,
énergie sur Android, mémoire TV après 1–2 heures, volume de trafic au repos.
Comparer les mêmes scénarios sur un appareil réel. Ne supprimer aucun cache
contenant des téléchargements ou données utilisateur sans preuve.

## Backend et indépendance : critères indispensables

1. Contrôler en lecture seule `/ready` de **son propre Worker** :
   `schema=0004_profile_media_gifs`, `profilePinSchema=1`,
   `catalogueSnapshotSchema=1`. Un code 200 de `/health` seul ne suffit pas.
2. Tester PIN, médias des profils, bibliothèque, progression et suppression
   entre trois appareils réels, avec un compte de test distinct si possible.
3. Tester le lancement et une lecture avec les anciens domaines Nuvio bloqués
   sur le réseau. Distinguer les flux demandés aux addons tiers des connexions
   d'infrastructure de Kovelyx. Aucun audit de chaînes ne prouve à lui seul
   le trafic réel DNS/TLS.
4. Restaurer **hors production** une sauvegarde D1 avec **sa clé privée** ;
   comparer comptes, PIN, médias, catalogues, bibliothèque et progression avec
   la source. Ne pas transférer de clé privée, SQL en clair ou secret Better Auth
   dans GitHub ni dans une conversation.
5. Préparer une instance portable sur un domaine HTTPS personnel, en test ;
   respecter la signature APK et l'identifiant de paquet lors d'une future
   migration. Voir [PORTABILITE_BACKEND.md](PORTABILITE_BACKEND.md) et
   [RESTAURATION_CLOUDFLARE_ET_CONFIDENTIALITE.md](RESTAURATION_CLOUDFLARE_ET_CONFIDENTIALITE.md).
6. Vérifier les obligations de redistribution des composants natifs, y compris
   les anciens commits MPVKit mentionnés dans [PROVENANCE.md](PROVENANCE.md).

## GitHub : verrouiller la maintenance

- Depuis **Settings → Rules → Rulesets**, cibler `main` : empêcher la
  suppression/force-push, exiger une PR et les contrôles requis. Régler les
  exceptions et vérifier les droits du compte avant activation. Le connecteur
  du projet ne peut pas modifier directement cette protection administrative.
- Conserver la politique **4 premières + 4 dernières Releases** et l'archive
  native séparée ; ne jamais effacer automatiquement les tags utilisés par
  l'application, ni remplacer une Release déjà distribuée.
- Examiner les PR ouvertes anciennes **individuellement** : ne pas fusionner
  des corrections datant d'une ancienne architecture sans tests comparatifs.
- Après validation, éviter les nouvelles fonctionnalités : n'accepter que
  correctifs reproductibles, sécurité et compatibilité, avec tests et
  publication signée sur le même commit.
- Vérifier régulièrement les dépendances et avis de sécurité (Gradle, npm,
  bibliothèques natives, Node, codecs), la date d'expiration des certificats,
  les restaurations et la consommation de stockage.
- Conserver les sauvegardes chiffrées, sources et outils **hors GitHub** :
  les artifacts de sauvegarde GitHub sont limités à 90 jours.

## Condition de clôture

Projet en maintenance stable **seulement quand** : contrôles CI verts,
publication signée complète, essais physiques validés, flux réseau vérifié,
restauration privée testée, schéma backend de production confirmé et `main`
protégée. Tout échec doit être reproduit, corrigé puis testé **avant** une
nouvelle publication. Ces conditions améliorent la durabilité sans promettre
une indépendance absolue vis-à-vis de l'OS, des catalogues ou de l'hébergeur.
