package com.nuvio.app.core.cloud

/**
 * Le jeton Better Auth ne doit jamais être stocké en clair.
 * Chaque plateforme choisit son stockage sécurisé ou refuse la persistance.
 */
internal expect object CloudSessionVault {
    fun load(): String?
    fun save(payload: String)
    fun clear()
}
