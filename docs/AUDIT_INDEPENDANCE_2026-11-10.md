# Kovelyx : audit d'indépendance du 10 novembre 2026

## Conclusion et périmètre

Aucune dépendance indispensable à un service ou dépôt Nuvio n'a été identifiée dans la branche principale et les trois applications publiées v1.1.1078. Ce constat couvre les scénarios et fichiers ci-dessous ; il ne garantit pas tous les codecs, extensions ou appareils physiques.

Sources principales examinées : commit `c61eb2288cea36daac5a99e88596e17e97037bd6`. Renforcement de l'audit fusionné dans `main` en `2b6a8d911191c56fa386bbcdcc308febbc61b054` : [PR #109](https://github.com/anderson76389/Kovelyx/pull/109).

## Preuves

| Contrôle | Résultat et limite |
|---|---|
| Code de production et Gradle | 2 757 fichiers, aucun ancien endpoint actif interdit ; aucun ancien SDK de compte dans les sources de production |
| Inventaire local de main | 5 841 fichiers suivis sur main avant l'ajout du nouveau test (5 842 sur la PR validée), 187 archives/binaires, aucun sous-module Git ou lien symbolique externe, aucune erreur de lecture |
| Binaires natifs conservés | 40 archives, 6 397 composants inspectés, aucun ancien domaine en clair |
| Reconstruction des moteurs | [Windows P2P et AAR Android quatre architectures réussis](https://github.com/anderson76389/Kovelyx/actions/runs/42537229248), sans remplacer les binaires de production ; identité binaire stricte non certifiée |
| Compilation sans Nuvio | [Mobile, TV et tests Desktop réussis sur main](https://github.com/anderson76389/Kovelyx/actions/runs/42550959521) |
| Backend et synchronisation | [Vrai Worker et D1 locaux, trois sessions simulées, profils, bibliothèque, suppressions, vus, reprise et catalogues réussis sur main](https://github.com/anderson76389/Kovelyx/actions/runs/42550959459) |
| Serveur réel, lecture seule | `/ready` HTTP 200 ; `status=ok`, schéma `0004_profile_media_gifs`, `catalogueSnapshotSchema=1`, `profilePinSchema=1`. Cela ne teste pas une session privée réelle |
| Contrat des mises à jour v1.1.1078 | Liens et fichiers SHA-256 des cinq paquets MSI, EXE, portable, mobile et TV valides |
| Tests locaux du backend | 34 réussis (9 Node, 25 Python). Le test local complet des comptes est bloqué ici par `uv_interface_addresses`; son exécution GitHub sur main a réussi |
| Tests du scanner renforcé | 10 réussis : archives imbriquées, UTF-16, MSI, erreurs/limites, registre des suffixes et identité des classes examinées |

Les quatre workflows finaux de la PR ont réussi. Les deux jobs du scan complet et les deux émulateurs sont validés. Les tests d'exécution téléchargent les vrais installateurs publiés. Le scan final confirme zéro ancien endpoint actif ou SDK de compte en clair, zéro erreur de lecture, deux entrées de suffixe public et une classe de rejet examinée. Voir le [scan final des trois paquets](https://github.com/anderson76389/Kovelyx/actions/runs/42731650897), les [deux essais Android sous blocage DNS](https://github.com/anderson76389/Kovelyx/actions/runs/42731650884) et le [démarrage Windows sans Nuvio](https://github.com/anderson76389/Kovelyx/actions/runs/42731650880).

## Mentions historiques examinées

- Six profils d'optimisation Android contiennent encore des noms de classes d'anciens fournisseurs de comptes. Ces indications ne sont pas du code exécuté ni un SDK compilé.
- Trois fichiers ProGuard conservent également des règles pour des classes retirées. Ces règles ne téléchargent et ne compilent aucun SDK.
- Des noms de DLL, symboles ABI, ressources et notices d'origine restent présents. Leur présence ne constitue pas une connexion à Nuvio. Les anciennes branches, tags et commits sont conservés.
- Le domaine de l’ancien fournisseur figure comme entrée autonome du registre standard `PublicSuffixDatabase.list`, utilisé pour les limites des domaines/cookies. Seule cette ligne exacte est classée comme registre ; une URL reste signalée.
- La classe Windows `ServerDiscoveryPolicy.class` contient les domaines qu'elle refuse. Son bytecode a été extrait de la distribution v1.1.1078 et examiné : `isOfficial` compare les noms d'hôtes, `parse` l'appelle puis lève `OfficialServer`. Aucun appel HTTP n'est effectué dans cette classe. L'exception du scan exige son chemin exact et le SHA-256 `4c67aae96272400ccadf4f8b71ecc1dd56b994abd56e7ef79b589975a0197b4b` ; toute modification nécessite un nouvel examen.
- L'ancien importeur contient des références de provenance NuvioMedia. Le test avec Git bloqué confirme qu'il ne les consulte pas lorsque les trois dossiers de sources, déjà conservés dans Kovelyx, existent.

## Dépendances restantes et limites

Cloudflare Workers/D1 pour les comptes et la synchronisation, GitHub pour les mises à jour et archives Kovelyx, les services de métadonnées et les extensions configurées restent des dépendances externes distinctes de Nuvio. La suppression des sources ou serveurs Nuvio ne supprime pas les copies conservées dans Kovelyx.

Aucun accès direct aux appareils physiques de l'utilisateur n'a été utilisé. Une navigation de base sur émulateur et le lancement Windows ne certifient pas tous les codecs, P2P, débridages, extensions, URL construites/chiffrées, services tiers et scénarios de mise à jour réels. Les résultats des tests backend avec sessions simulées ne remplacent pas une vérification complète de l'interface sur trois appareils.
