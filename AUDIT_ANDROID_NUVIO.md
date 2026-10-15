# Audit Android : indépendance de Nuvio, comptes conservés

**Date : 15 octobre 2026 — état du code, pas certification d'un téléphone.**

## Résultat vérifié automatiquement

Le contrôle `scripts/audit-mobile-independence.py` a réussi sur le dépôt Kovelyx (workflow **Vérifier la parité visuelle Windows et Android**). **858 fichiers Kotlin Android** ont été inspectés.

| Contrôle | Résultat |
| --- | --- |
| URL Nuvio non liées aux comptes, codées dans les sources Kotlin Android actives | **0 trouvée** |
| URL `https://nuvio.tv/link` | **1**, conservée **uniquement** pour le mécanisme facultatif de connexion aux comptes |
| URL `github.com/NuvioMedia/NuvioMobile` | **1**, conservée pour l'**attribution juridique** de la source d'origine |
| Vérification des mises à jour | Pointe vers les **GitHub Releases du dépôt Kovelyx** |
| Moteur Android | AAR `apps/mobile/composeApp/libs/lib-nuvio-engine-android-0.1.4.aar` conservé **localement**, avec des sources de moteur sauvegardées dans `vendor/engine` |
| Nom du projet de compilation Gradle Android | `Kovelyx` |
| Bibliothèques publiées par Nuvio sur un serveur distant obligatoires à l'exécution | Aucune **démontrée par l'inspection des sources Kotlin**, mais un test réseau de l'APK reste requis |

Le test CI échouera désormais si une URL Nuvio non autorisée est ajoutée à du code Kotlin compilé. Il ne fait aucune requête vers Nuvio ; c'est une analyse de fichiers.

## Exception volontaire : comptes Nuvio

La connexion existante (`DeviceLinkAuthRepository.kt`, `nuvio.tv/link`, backend Supabase paramétrable, stockage de session, synchronisation) est **conservée**. C'est une dépendance réseau **réelle, mais autorisée** par la demande actuelle. Elle peut cesser de fonctionner si le service de comptes Nuvio ferme.

La configuration du backend ne constitue **pas** un nouveau serveur de comptes Kovelyx. Les valeurs `NUVIO_SUPABASE_URL`, `NUVIO_SUPABASE_ANON_KEY` et `NUVIO_SUPABASE_FALLBACK_URL` sont des clés historiques de configuration de l'authentification, pas des URL codées en dur. Si l'APK est construit sans ces valeurs, **la connexion au compte distant ne doit pas être présentée comme opérationnelle sans test**. Les autres fonctions locales peuvent être utilisées séparément selon leur propre configuration.

Les fonctionnalités héritées de l'abonnement ou des cosmétiques de compte (`MemberAccessRemoteDataSource.kt`, RPC `get_my_member_access`) utilisent aussi ce backend de compte : **elles restent dépendantes de ce service**, et il n'existe pas encore de remplacement totalement autonome.

## Dépendances qui ne sont pas des appels à Nuvio

- **`com.nuvio.app`** : espace de noms historique dans le code Kotlin ; ce n'est pas une URL. Le renommer massivement sans migration risquerait de casser JNI, stockage, intégrations et données.
- **`libnuvio_engine.so` / AAR** : bibliothèque native de lecture/transfert empaquetée dans l'APK. La présence locale signifie qu'elle ne nécessite pas forcément de téléchargement depuis Nuvio pendant la lecture, mais **le caractère entièrement reproductible à partir des sources reste à prouver**.
- **`github.com/NuvioMedia/NuvioMobile`** en attribution : notice de provenance du code. Les licences obligent à conserver des attributions adaptées ; supprimer ces crédits serait différent de supprimer une dépendance technique.
- **Trakt, TMDB, MDBList, AllDebrid, Real-Debrid, extensions et sources configurées** : services externes tiers, pas des services Nuvio. Leur bon fonctionnement dépend des clés et des réseaux respectifs.
- Les anciennes références iOS, Android TV, `store.json` et traductions historiques ne constituent pas à elles seules une requête réseau lancée par **l'APK Android**. Elles devront être traitées séparément pour nettoyer complètement les sources et l'interface.

## Limites explicites

Il serait **inexact** d'affirmer que l'APK est **« 100 % indépendant de Nuvio »** sur ce seul audit. Pour le démontrer, il manque au moins :

1. Un essai réel sur téléphone avec captures DNS/HTTP après avoir **bloqué les domaines Nuvio** (en conservant volontairement l'accès au compte dans le scénario séparé), puis tests de lecture, recherche, addons, profils et mises à jour.
2. Une inspection des chaînes réseau et des dépendances **dans les binaires natifs et dans le fichier APK signé**, et pas seulement dans les sources Kotlin.
3. Une reconstruction du moteur Android **depuis les sources sauvegardées**, comparée à la bibliothèque AAR actuellement empaquetée.
4. Des tests d'échec du backend des comptes, pour confirmer que les fonctions locales continuent de marcher en mode invité.
5. Une clé de signature Android stable, privée et maîtrisée par Kovelyx ; les APK Debug CI utilisent une signature de test non garantie identique entre builds.

**Conclusion actuelle :** les appels directs Nuvio non liés aux comptes n'ont pas été trouvés dans **les 858 fichiers Kotlin examinés**, le code de mise à jour utilise Kovelyx, et les composants natifs nécessaires sont stockés dans le dépôt. L'exception des comptes reste active et l'absence de dépendances réseau cachées **n'est pas encore prouvée**.
