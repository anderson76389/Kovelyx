# Migration des noms techniques hérités vers Kovelyx

Ce document distingue les **références cosmétiques** des **identifiants qui constituent un contrat de compatibilité**. Supprimer le texte « Nuvio » ne suffit pas à rendre une application indépendante ; inversement, une ancienne chaîne n'implique pas un appel réseau.

## Nettoyage sans migration de données

- Les tests de paquet Linux utilisent des noms et un mainteneur fictif Kovelyx, sans adresse Nuvio.
- La découverte des serveurs personnalisés Android mobile et Windows n'utilise plus de constante « backend officiel » redondante. Les filtres DNS sur `nuvio.tv` et `nuvioapp.space` **restent en vigueur**, de même que l'interdiction des redirections vers ces domaines.
- Les types de jetons graphiques `KovelyxMediaColors`, `KovelyxMediaTokens` et `KovelyxMedia` remplacent leurs anciens noms dans le thème Android TV. Cela ne touche ni les préférences utilisateur ni l'apparence.

## Identifiants à migrer séparément, pas à effacer aveuglément

| Élément | Pourquoi le renommage immédiat peut casser l'existant | Stratégie sûre |
| --- | --- | --- |
| Packages Kotlin `com.nuvio.*` | Milliers d'importations, composants Android, références de classes et générateurs de code | Migration coordonnée des sources + manifests + réflexion et tests de compilation des trois cibles |
| `applicationId` / identifiant d'installation | Android voit un nouvel ID comme une autre application : les anciennes données et la mise à jour en place ne suivent pas | Garder l'ID stable pour les utilisateurs existants, ou créer une migration/export-import explicite vers une nouvelle application |
| Schémas `nuvio://` et callbacks OAuth | Anciennes URL, lecteurs externes, connexions et intégrations peuvent utiliser ces schémas | Déclarer `kovelyx://` comme canonique et maintenir l'ancien comme alias de réception pendant la transition |
| Clés de stockage et dossiers `nuvio-engine` | Renommer sans copier peut faire disparaître caches, réglages et état local | Migrer atomiquement les anciennes clés et dossiers avant d'arrêter de les lire ; tests de mise à jour depuis une version existante |
| `nuvio_engine.dll`, `libnuvio_engine.so`, noms ABI/JNI et symboles natifs | Les exécutables, la FFI et les bibliothèques doivent utiliser **exactement** les mêmes noms et symboles | Reconstruire toutes les bibliothèques et remplacer de façon coordonnée les points de chargement ; vérifier licences et provenance |
| Tags, anciennes Releases et archives de récupération | Les réécrire détruit la traçabilité et les moyens de restauration | Conserver les artefacts nécessaires hors des chemins d'exécution ; ne purger qu'après validation des remplaçants |

## Validation avant généralisation

1. Compiler et tester Windows, Android mobile et Android TV, ainsi que leurs lecteurs internes/externes.
2. Installer une mise à jour **par-dessus** une version antérieure ; retrouver comptes, addons, bibliothèque, progression, profils et préférences.
3. Bloquer `nuvio.tv` et `nuvioapp.space` pendant les parcours réseau ; vérifier les appels réels et le fonctionnement Cloudflare/D1.
4. Contrôler les binaires, les signatures, l'ABI et le retour arrière avant toute suppression des moteurs historiques.
5. Conserver les mentions d'attribution et les licences obligatoires, même si le branding et les noms internes sont migrés.

Les références réseau de blocage et les fixtures de régression sont **intentionnelles** : les retirer sans remplacement affaiblirait les tests d'indépendance. Ce document n'est pas une certification de migration complète.
