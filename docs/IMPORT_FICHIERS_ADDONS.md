# Importer des fichiers JSON d'extensions dans Kovelyx

Dans **Extensions → Importer un fichier JSON d'extensions** (Android et Windows/Linux selon le sélecteur de fichiers de la plateforme), sélectionnez une sauvegarde contenant les URL publiques des manifests. Le contenu du fichier est **validé sur l'appareil** et chaque installation est confirmée.

Formats reconnus :

```json
{
  "addons": [
    { "manifestUrl": "https://mon-addon.exemple/manifest.json" },
    { "manifestUrl": "https://mon-autre-addon.exemple/config/manifest.json" }
  ]
}
```

Un tableau de chaînes HTTPS ou un seul objet avec `manifestUrl` est aussi accepté. La liste est limitée à 25 manifests par fichier ; les doublons sont ignorés. Chaque URL est installée à l'aide du gestionnaire normal des addons, sans exécuter du code embarqué dans le JSON.

**Un `manifest.json` Stremio isolé ne suffit pas à installer une extension** : les endpoints des catalogues et des autres ressources doivent être accessibles sur un serveur HTTPS. Un ZIP sauvegardant un site (par exemple une capture de site Internet) n'est pas un addon installable ; les exports de collections vont dans **Collections → Importer JSON**, déjà présent dans Kovelyx. Ne téléversez pas le contenu du ZIP vers un service tiers pour le convertir.

Les fichiers ne contiennent pas les résultats des catalogues. Installer un addon ou une clé MDBList ne garantit pas que les sélections TMDB/Trakt deviennent accessibles : les sources de chaque dossier restent à configurer, avec la possibilité qu'un fournisseur soit indisponible.

Sur Android TV, **Collections → Importer JSON** accepte déjà les fichiers de collections. L'import de sauvegardes d'addons par fichier dans l'interface TV reste une amélioration distincte (avec choix adapté à la télécommande).
