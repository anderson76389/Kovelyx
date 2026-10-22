# Vérification des paramètres Android TV — 22 octobre 2026

## Périmètre
Audit du code de la branche `main` de Kovelyx TV, et comparaison avec les améliorations de la PR #40. Ce document n'est **pas** une attestation de fonctionnement sur une TV physique.

| Rubrique du menu TV | Code et navigation | Vérification / restriction |
| --- | --- | --- |
| Compte | `SettingsScreen`, `AccountSettingsContent`, `AuthManager` | Better Auth/D1 configurables, mais la Release garde une session Cloudflare uniquement en mémoire. La PR #40 prévoit coffre AndroidKeyStore et synchronisation. |
| Profils | `ProfileSettingsContent`, `ProfileManager` | Réservé au profil principal. Personnalisation cloud R2 non activée sur production. |
| Apparence | `ThemeSettingsScreen`, `ThemeSettingsViewModel` | Réglages présents ; rendu/focus sur appareil réel non validés. |
| Disposition | `LayoutSettingsScreen` et modèles | Différentes vues et tailles configurables. |
| Découverte / catalogues | `ContentDiscoverySettingsContent`, gestion des addons/plugins | Dépendent de services et indexeurs tiers. |
| Lecture Media3/MPV | `PlaybackSettingsScreen`, `PlayerSettingsDataStore` | 10 sections : moteur, sources, épisode suivant, segments, interface, audio, sous-titres, vidéo, tampon/réseau, P2P. Les options réseau spécifiques à ExoPlayer sont masquées en mode MPV seul. |
| Débrid | `DebridProviders`, `DebridSettingsViewModel` | **Écart : AllDebrid non intégré dans cette couche.** Torbox et Premiumize visibles ; Real-Debrid enregistré mais `visibleInUi=false`, sa validation de clé n'est pas codée dans le ViewModel. |
| Suivi | `TrackingSettingsScreen` | Trakt et MDBList visibles. **Écart : SIMKL reste visible** alors que sa suppression est souhaitée. |
| Avancé | `AdvancedSettingsViewModel` | État de préférences et réinitialisation présents. |
| À propos / mises à jour | `UpdateChannelSettings`, `UpdateViewModel` | Canaux stable/bêta, notification et recherche manuelle. Le tag `alpha` demeure une préversion et ne démontre pas la stabilité. |
| Debug / expérience | `SettingsCatalog` | Debug réservé aux builds debug et masqué en mode Essential ; l'expérience se configure via Avancé. |

## Anomalies corrigées dans cette PR
- `SettingsValueRows.kt` : la carte de réglage de curseur était focalisable, mais son action « OK » était vide. Désormais « OK » avance d'un cran si le contrôle est activé. Les commandes gauche/droite et −/+ sont préservées.
- `CloudflareTvAccountClient.kt` : les requêtes natives Better Auth de connexion et déconnexion n'envoyaient pas `Origin`. Les deux POST utilisent désormais l'URL HTTPS du Worker. Le résultat HTTP de déconnexion distante est contrôlé ; l'absence de connexion distante n'efface pas la sortie locale.

## Preuves et limites
- Les 327 fichiers de tests TV (unitaires + Android instrumentés, Media3 inclus) sont présents, mais cela ne signifie pas 327 tests passés en conditions réelles.
- La PR #40 est passée en vert sur les workflows de tests TV, émulateur Media3 et compilation sans domaines Nuvio. **Elle n'est pas fusionnée sur `main`.**
- Vérification spécifique à cette PR : compilation et suite des tests unitaires TV exigées par `tv-settings-controls-check.yml`, sans les ignorer.
- Une attestation complète nécessite une TV Android physique : navigation et boutons de chaque rubrique, connexion/reconnexion après redémarrage, fichiers débrid, vidéo réelle avec Media3, progression et synchronisation D1, reprises après coupure réseau.
- Ne pas annoncer « 100 % des paramètres fonctionnels », « AllDebrid pris en charge » ou « synchronisation TV complète » avant ces validations.
