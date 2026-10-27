# Kovelyx Android téléphone / tablette — audit comparatif avec Nuvio (27 octobre 2026)

> Audit **statique** des sources Kovelyx et de l'APK Nuvio fourni par le propriétaire.
> Ne constitue **pas** une attestation de lecture vidéo, réseau ou synchronisation sur appareils physiques.

## APK d'origine inspecté

- Fichier de référence : `androidApp-full-x86_64-release(1).apk` (fourni dans la conversation, pas ajouté au dépôt).
- SHA-256 : `a91ac74e261a870573402514dd4335d45f3293d09a97b49e1cb4ee92da450db8`.
- Taille : 47 314 273 octets ; 1 063 entrées ZIP ; trois classes DEX ; **x86_64 uniquement**.
- Version publiée dans le manifeste : `0.6.4-beta`; identifiant Android : `com.nuvio.app`.
- Bibliothèques natives observées : `libnuvio_engine.so`, `libmpv.so`, `libmedia3_exoplayer_jni.so`, `libavcodec.so`, `libavformat.so`, `libasskt.so`, `libquickjs.so`, etc.
- Chaînes d'URL présentes dans les DEX de l'original : `https://api.nuvio.tv`, `https://api-two.nuvioapp.space`, `https://nuvio.tv/link`, `https://github.com/NuvioMedia/NuvioMobile`, `https://nuvio.tv/api/supporters/wall`. Ces chaînes prouvent seulement **leur présence dans l'original**, pas leur utilisation ni leur éventuelle présence dans les APK Kovelyx.

## Ce qui est indépendant dans les sources Kovelyx

- Android : `applicationId = "com.kovelyx.mobile"` en Release ; identifiant distinct `com.kovelyx.mobile.debug` pour les APK Debug.
- Noms et icônes de lanceur : **Kovelyx** ; liens d'authentification `kovelyx://`.
- La configuration CI renseigne un **Worker Cloudflare** pour Kovelyx ; elle génère volontairement des URL / clés Supabase historiques **vides** dans les nouveaux builds.
- Client `CloudflareAccountClient` pour les comptes Better Auth / D1 et `CloudflareDataSync` pour la bibliothèque, la progression et l'historique vu.
- Recherche de mises à jour via `https://api.github.com/repos/anderson76389/Kovelyx`.
- Les catalogues historiques d'avatars, fonds et avantages « membres » sont contournés lorsque Cloudflare est activé (PR #65 et #66).

## Corrections de ce lot

1. La classe d'application Android `NuvioApplication` devient `KovelyxApplication`, **sans changer le chargeur d'images Coil** et sans modifier `applicationId`.
2. `ServerDiscoveryPolicy` rejette les hôtes `nuvio.tv`, `*.nuvio.tv`, `nuvioapp.space` et `*.nuvioapp.space`, y compris le backend suggéré par un fichier de découverte.
3. Les requêtes de découverte ne suivent **aucune redirection HTTP** : un serveur personnalisé ne peut donc pas les diriger vers l'infrastructure Nuvio.
4. Une ancienne adresse Nuvio enregistrée en tant que serveur personnalisé **n'est plus activée** au démarrage ; elle n'est pas supprimée du stockage utilisateur.
5. Tests de non-régression Kotlin : hôtes Nuvio bloqués, faux positifs de domaine évités, auto-hébergement restant compatible.
6. Audits Python : suppression de l'exception d'URL de connexion Nuvio et garde-fou pour le manifeste et la nouvelle classe Application.
7. L'ancienne URL d'aide Debrid `nuvioapp.space/faq` encore active dans les réglages a été remplacée par `docs/AIDE_DEBRID_ANDROID.md`, hébergée dans Kovelyx. L'audit renforcé a détecté cette référence et bloqué la PR avant correction.

## Héritages encore présents, intentionnellement

| Élément | Pourquoi il reste | Dépendance aux serveurs Nuvio ? |
|---|---|---|
| Espaces de noms `com.nuvio.*` / `nuvio.composeapp.generated.resources` | Renommage global Kotlin, Compose Resources et migrations de stockage à réaliser avec prudence | **Non en soi** |
| `lib-nuvio-engine-android-0.1.4.aar`, `quickjs-kt-android-1.0.5-nuvio.aar` | Binaires conservés dans le dépôt ; provenance et reconstruction complète à vérifier séparément | Ne prouvent pas un appel réseau Nuvio ; **autonomie de compilation binaire non certifiée** |
| `/.well-known/nuvio` et `service = "nuvio"` | Protocole de découverte des anciens serveurs **personnalisés auto-hébergés** | Compatible avec un serveur tiers ; l'accès aux **domaines Nuvio est désormais refusé** |
| Bibliothèques Supabase / RPC de connexion héritées | Modes historiques spécifiques et compatibilité ; le chemin Cloudflare ne les utilise pas pour le compte principal | À tester dynamiquement pour exclure toute route inattendue |
| Simkl, TorBox et autres fournisseurs historiques | Modules tierce partie potentiellement utilisés par des écrans et la lecture ; suppression massive risquée | **Tiers, pas des serveurs Nuvio** |
| Références de crédits et licences d'origine | Respect des licences de code dérivé GPL et notices tierces | **Aucune dépendance de service** |

## Ce qu'il reste à prouver avant « 100 % fonctionnel »

- Compiler et exécuter les tests de la branche après correction ; produire de nouveaux APK installables et vérifier signature/identifiant/label.
- Tester sur **un téléphone ARM64 et une tablette** : démarrage, mise à jour, navigation et orientation, codecs, téléchargements, addons, déconnexion, import photo/fond.
- Capturer le trafic pendant ces parcours, avec les domaines amont bloqués, pour vérifier **l'absence d'appel effectif** à Nuvio.
- Tester une vraie session commune Windows + téléphone + tablette/TV : bibliothèque, reprise de position, œuvres vues, suppressions, déconnexion et reconnexion.
- Vérifier la provenance, les licences, les sources et la reproductibilité du moteur Android et du plugin QuickJS intégré.
- Garder une signature Android constante pour les mises à jour conservant les données locales.

**Conclusion :** le code Kovelyx Android sépare l'identité installée, les mises à jour et le backend de comptes de Nuvio. Les anciennes bibliothèques natives et espaces de noms empêchent néanmoins de qualifier l'application de « réimplémentation entièrement autonome » sans audit binaire et tests matériels supplémentaires.
