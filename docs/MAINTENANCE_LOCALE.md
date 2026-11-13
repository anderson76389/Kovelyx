# Stockage, synchronisation et sauvegardes locales

Les paramètres avancés de Windows, Android mobile et Android TV regroupent ces outils. Ils fonctionnent localement et n'ajoutent aucun service réseau.

## Stockage

Les tailles concernent les fichiers accessibles à l'application : paquet installé quand il est identifiable, données locales, caches connus, installateurs temporaires et opérations de maintenance. Elles peuvent différer du total affiché par le système, qui compte aussi les bibliothèques extraites, les blocs alloués et d'autres fichiers système. Les caches mémoire ne sont pas inclus.

Un nettoyage demandé est appliqué au prochain lancement complet du processus, avant le démarrage des lecteurs et chargeurs d'images. Cela évite de retirer des fichiers encore ouverts. La liste des dossiers nettoyables est fixe ; les vidéos téléchargées, profils, bibliothèques, sauvegardes et moteurs exécutables ne sont pas nettoyés. Les installateurs conservent leur nettoyage automatique et leurs protections de mise à jour existantes.

## Synchronisation

Le diagnostic décrit le compte et le profil actifs au moment du relevé. Il affiche la dernière réussite **observée depuis le lancement**, une demande en attente, une opération en cours ou un échec temporaire. Les détails d'erreur, URL, jetons et contenus de bibliothèque ne sont jamais affichés dans ce diagnostic.

Le nombre de mutations de bibliothèque vient de la file réelle. La TV compte aussi les opérations de progression et de statut vu. Les autres catégories sont incluses dans le résultat global ; un nombre de bibliothèque à zéro ne certifie pas à lui seul que tout est synchronisé. Utiliser « Actualiser le diagnostic » pour refaire le relevé.

## Sauvegarde et restauration

1. Mettre la lecture en pause et éviter les modifications de réglages ou profils pendant l'opération.
2. Choisir un mot de passe d'au moins 12 caractères, le confirmer, puis créer un fichier `.kovbackup` avec le sélecteur système.
3. Conserver le fichier sur un support distinct de l'appareil, avec le mot de passe séparément.
4. Pour restaurer, se connecter au même compte et utiliser le même type d'application. L’identifiant du compte doit être conservé lors d’une migration du serveur. Les sauvegardes invitées restent distinctes des comptes connectés.
5. Saisir le mot de passe, confirmer la restauration puis sélectionner le fichier. L'application valide entièrement l'archive avant de préparer une modification.
6. Fermer complètement Kovelyx puis le relancer. Avant le redémarrage, la restauration peut encore être annulée depuis les paramètres.

Le contenu comprend les préférences locales, bibliothèques, progression, statuts vus, profils, configuration des extensions et médias privés de profil dans les dossiers pris en charge. Les sessions et jetons de connexion, connexions aux serveurs multimédias, configuration de connexion au backend, caches de PIN, identifiants des files de synchronisation, téléchargements vidéo, caches et fichiers exécutables de plugins sont exclus. Les plugins exécutables doivent être réinstallés si leurs fichiers ne sont plus présents. Les sauvegardes de l'application ne remplacent pas les sauvegardes du serveur de comptes.

Les données remplacées correspondent aux fichiers présents dans l'archive. Les fichiers personnels extérieurs à ces dossiers ne sont pas modifiés. Les règles habituelles de synchronisation reprennent après restauration ; une sauvegarde locale n'est pas un retour à une date précédente de la base distante. Les références absolues aux médias peuvent nécessiter une nouvelle sélection si le chemin du dossier utilisateur change sur un autre ordinateur.

Le format version 1 utilise PBKDF2-HMAC-SHA1 avec sel aléatoire de 128 bits et 600 000 itérations pour une clé AES-256. Le flux est chiffré par blocs AES-GCM de 64 Kio, chacun avec nonce, numéro de séquence et longueur authentifiés. Un bloc final authentifié et la fin du fichier sont obligatoires. Le déchiffrement est borné et aucune donnée n'est analysée avant validation de tous les blocs. Aucun mot de passe n'est sauvegardé ou journalisé.

La restauration prépare un journal local et une copie des fichiers précédents. Elle vérifie aussi que les fichiers de session n'ont pas changé depuis la préparation. En cas d'échec d'écriture ou de reprise après interruption, elle remet les fichiers précédents avant de laisser les magasins de données démarrer. Cela ne constitue pas une garantie contre une panne physique du disque ; la copie externe reste nécessaire.

## Vérification

Les sources communes se trouvent dans `shared/maintenance*`. Le workflow `maintenance-safety.yml` teste le même moteur sur Windows et Linux, y compris mots de passe incorrects, autres comptes/types d'application, fichiers tronqués/altérés, doublons, sorties de dossiers, liens symboliques, restauration interrompue, rollback et nettoyage différé. Un scénario de 24 Mio vérifie le traitement en flux avec un tas JVM limité à 64 Mio.

Les compilations et suites Kotlin des trois applications, la synchronisation Worker/D1, les lecteurs et les mises à jour sont des contrôles complémentaires. Les essais sur émulateur ne certifient pas tous les matériels physiques, fournisseurs de fichiers, codecs ou services externes. Aucun test fini ne peut prouver l'absence de toute fuite ou garantir que des services tiers fonctionneront éternellement.
