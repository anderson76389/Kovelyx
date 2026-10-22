package com.nuvio.app.features.profiles

/**
 * Import natif : choisit un média, en fait une copie privée indépendante du
 * document original et retourne son URI locale. Aucun transfert vers un tiers
 * n'a lieu lors du simple choix d'un fichier.
 */
internal expect object ProfileLocalImages {
    val supported: Boolean
    suspend fun chooseImage(background: Boolean): String?
    fun isManagedUri(url: String): Boolean
    fun savedImage(userId: String, profileIndex: Int, background: Boolean): String?
    fun saveImage(userId: String, profileIndex: Int, background: Boolean, url: String?)
}
