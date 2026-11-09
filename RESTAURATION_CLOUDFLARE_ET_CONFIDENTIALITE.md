# Sauvegarde D1 et confidentialité — Kovelyx

## Ce qui est réellement protégé

- La sauvegarde du Worker exporte D1 en lecture seule, vérifie la liaison exacte du Worker et du compte, puis chiffre la copie **AES-256-GCM (CMS)** avec le certificat de récupération public.
- Le SQL en clair reste **temporaire sur le runner**, n'est pas publié comme artifact et est restauré dans un SQLite **en mémoire** pour tester intégrité, clés étrangères, comptes, bibliothèque, progression, historique, médias et catalogues publiés. La présence et l'intégrité des profils PIN D1 0005 sont désormais également vérifiées.
- L'archive GitHub Actions contient le fichier chiffré `kovelyx-d1.sql.cms`, un manifeste d'empreintes SHA-256 et, si configuré, une copie **chiffrée** du bundle de signature Android. **Elle ne contient jamais le secret Better Auth.** Celui-ci doit être sauvegardé séparément et en sécurité.
- La tâche planifiée `.github/workflows/cloudflare-backup.yml` est prévue **chaque jour à 03:40 UTC**. Les artefacts GitHub sont conservés **90 jours seulement** : cela ne constitue **pas** à lui seul une sauvegarde « à vie ».
- Le workflow refuse de publier un artifact comme sauvegarde valide si l'export, le chiffrement ou les vérifications échouent. Une sauvegarde récente doit être contrôlée sur l'onglet **Actions** ; l'existence du script ne prouve pas que les identifiants Cloudflare sont actifs.

## Test de récupération réel, chez le propriétaire

1. Télécharger depuis **Actions → Sauvegarder les comptes et médias D1 → Artifacts** la dernière copie chiffrée ayant réussi, puis l'extraire dans un dossier privé.
2. Récupérer **sur son poste seulement** la clé privée correspondant au certificat `.github/cloudflare-backup-recipient.pem`. **Ne jamais l'envoyer à GitHub, ChatGPT ou un tiers**, ne jamais la commiter. Si elle a été perdue, générer une nouvelle paire de récupération et modifier le certificat public **avant** de nouveaux backups ; les anciennes archives ne seront pas déchiffrables sans l'ancienne clé.
3. Depuis la racine du dépôt :
   ```bash
   python3 services/kovelyx-cloudflare/scripts/verify-private-backup.py \
      --artifact-dir "/chemin/vers/backup-extrait" \
      --private-key "/chemin/prive/cle-de-recuperation.pem"
   ```
   Le programme contrôle le certificat, les empreintes CMS et SQL, déchiffre dans un dossier temporaire privé, restaure le SQL **uniquement en mémoire**, compare le nombre de lignes pour chaque table, ainsi que les médias et catalogues. Il ne se connecte à **aucun Worker Cloudflare** et ne déploie aucune migration.
4. Copier l'archive chiffrée **hors de GitHub** vers deux emplacements personnels distincts. Conserver la clé privée séparément de ces copies, et sauvegarder le secret Better Auth à part.
5. Toute restauration dans **D1 de production** exige encore un plan explicite de panne, un export récent de secours, une base D1 isolée de répétition et une validation humaine. **Aucun script ici n'efface ni ne remplace les profils de production.**

Les tests GitHub du protocole de récupération utilisent exclusivement un **certificat RSA temporaire** et une base de comptes jetable ; ils ne peuvent pas prouver que la **vraie clé privée**, détenue par l'utilisateur, est encore disponible.

## Audit confidentialité et réseau

Contrôles renforcés :
- Les journaux TV ne conservent plus les corps de réponse, les URL configurées et les messages bruts d'exception ; le lecteur garde les durées et classes d'erreur mais pas le nom du fichier ni les URL de flux.
- Dans les clients Windows/Android/TV, les breadcrumbs Sentry personnalisés ne conservent plus ni l'hôte, ni le chemin, ni les messages de requêtes : ces champs peuvent transporter des identifiants incorporés dans les liens d'addons. Les événements filtrent également les messages libres, tout en gardant leurs types et piles techniques.
- Les trois manifestes Android principaux déclarent `allowBackup=false` pour empêcher une sauvegarde automatique du stockage privé de l'application. Le déclenchement automatique Sentry est désactivé.
- `python3 scripts/tests/audit_network_surface.py` génère un inventaire **statique**, sans afficher les URL ni les jetons : nombre d'hôtes littéraux classés par service, permissions et autorisation HTTP des trois manifestes.

### Limitations et services externes

La lecture nécessite parfois des services de métadonnées, des addons choisis par l'utilisateur, des sous-titres, des trackers, des fournisseurs de lecture, Cloudflare et GitHub Releases. Le choix d'une URL **HTTP non chiffrée** pour un addon local reste possible (`usesCleartextTraffic=true`) : ce compromis évite de casser les addons personnels, mais n'apporte **aucune** confidentialité au trafic HTTP.

Ni cet audit ni les tests CI ne mesurent le **trafic réel sur une TV physique** ni ne garantissent la confidentialité de chaque addon ou dépendance tierce. L'inventaire des URL codées en dur ne voit pas les URL construites à l'exécution ; il ne sert donc **pas** de liste blanche et ne coupe pas les connexions nécessaires. Une validation réseau réelle doit se faire sur des appareils de test, avec captures **locales et expurgées**.

**Priorités à préserver :** ne jamais commiter de secret, ne pas publier de SQL D1 en clair, ne pas désinstaller l'APK pour changer de signature, ne pas purger la base avant d'avoir testé une vraie récupération.
