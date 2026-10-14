# ✅ Plan de vérification manuelle — Kovelyx PC

Ce plan complète les tests automatiques. **Cocher uniquement ce qui a été essayé sur un vrai PC**, en précisant la révision et le système utilisés.

## Fiche de test

| Champ | Valeur à renseigner |
|---|---|
| OS | Windows 10/11 ou Linux (distribution + version) |
| Architecture | x86-64 |
| Version et commit | SHA indiqué dans le nom du fichier |
| Format | EXE / MSI / ZIP portable / AppImage |
| Carte graphique et pilote | Modèle, version |
| Affichage | Résolution, mise à l'échelle ; X11 ou Wayland si Linux |

## Liste de contrôle

### Installation et identité

- [ ] Installation depuis le **Setup.exe** Windows, sans fichiers supplémentaires à télécharger manuellement.
- [ ] Lancement d'une **AppImage** x86-64 sur une distribution Linux compatible.
- [ ] Logo Kovelyx visible sur la fenêtre, le menu et les raccourcis.
- [ ] Fermeture puis second démarrage sans erreur.
- [ ] Aucun programme Nuvio lancé, ni redirection de mise à jour vers l'éditeur d'origine.

### Profils et données

- [ ] Création, modification, sélection et suppression d'un profil.
- [ ] Import d'un avatar JPG/PNG/GIF/BMP de moins de 12 Mo ; aperçu visible.
- [ ] Import d'un fond de profil et bascule vers le fond par défaut.
- [ ] Conservation des images après fermeture et redémarrage.
- [ ] Images différentes pour deux profils indépendants.
- [ ] Aucune image personnelle envoyée à un service distant.
- [ ] Mode invité sans serveur : création et stockage local fonctionnels.

### Catalogue et lecture

- [ ] Accueil, recherche, fiche d'un film et fiche d'une série.
- [ ] Installation, configuration et désinstallation d'une extension compatible.
- [ ] Lecture d'un flux autorisé, arrêt, reprise et avance rapide.
- [ ] Changement de piste audio ; sous-titres internes et externes.
- [ ] Plein écran, fenêtre réduite, changement de taille et second écran.
- [ ] Gestion de l'historique et de la bibliothèque après redémarrage.

### Paramètres, stabilité et sécurité

- [ ] Thèmes et paramètres conservés après redémarrage.
- [ ] Export ou sauvegarde des données avant une mise à jour.
- [ ] Aucun blocage en utilisant plusieurs extensions ou fenêtres.
- [ ] CPU/RAM surveillés au repos et pendant la lecture.
- [ ] Intégrité SHA-256 des fichiers distribués vérifiée.
- [ ] Messages d'erreurs compréhensibles, sans lien cassé vers Nuvio.

## Remonter un défaut

Ouvrir une [Issue GitHub](https://github.com/anderson76389/Kovelyx/issues) avec une description reproductible, la version, l'OS, le journal d'erreur et une capture **expurgée** de données privées, clés d'API et URLs de comptes. Distinguer **fonction absente**, **crash** et **fonction présente mais dysfonctionnelle**.

Le [workflow Windows](../.github/workflows/windows.yml) bloque les artefacts si les tests automatisés échouent ; le [workflow Linux](../.github/workflows/linux.yml) vérifie le format et la structure AppImage. Aucun de ces deux pipelines ne remplace la présente checklist.
