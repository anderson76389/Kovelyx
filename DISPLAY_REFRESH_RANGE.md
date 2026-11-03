# Fréquences d’affichage par appareil

Le réglage est dans **Avancé** sur Windows, Android mobile et Android TV, y compris dans l’affichage essentiel des paramètres TV.

Deux sélections distinctes définissent le minimum et le maximum. Seules les fréquences signalées par l’écran à sa résolution actuelle sont proposées ; le maximum autorisé est de 144 Hz. Les valeurs 90 Hz, 120 Hz et 144 Hz apparaissent si elles sont prises en charge. Les fréquences TV plus basses peuvent également servir de minimum si l’écran les propose. Une borne minimale supérieure à la maximale est refusée.

L’application demande le mode pris en charge le plus élevé de cette plage. Windows peut essayer les modes inférieurs de la même plage si le pilote refuse le premier ; il ne descend pas sous le minimum. Android utilise l’API publique du mode préféré de la fenêtre. Il ne s’agit pas d’un réglage VRR global ni d’une promesse de variation automatique entre les deux bornes : le système, le pilote et les capacités matérielles restent prioritaires. La fréquence réellement signalée est affichée, même lorsqu’elle est hors plage. Les vidéos conservent leur cadence d’origine.

Les deux bornes sont enregistrées ensemble sur l’appareil, indépendamment du compte cloud. Les anciens réglages sont repris : 60 Hz devient 60–60 Hz ; 120 Hz devient 60–120 Hz pour conserver son ancien repli à 60 Hz. Aucun changement de résolution ni écriture des réglages Windows dans le registre n’est demandé. À la fermeture, Windows restaure le mode antérieur si une autre application ou l’utilisateur ne l’a pas modifié depuis.

Les tests couvrent le plafond de 144 Hz, le minimum, les fréquences fractionnaires, la résolution, les valeurs invalides, la migration, la conservation des bornes et l’indépendance de deux appareils. Un test Android sur appareil jetable vérifie aussi le mode réellement demandé à la fenêtre et les préférences enregistrées.
