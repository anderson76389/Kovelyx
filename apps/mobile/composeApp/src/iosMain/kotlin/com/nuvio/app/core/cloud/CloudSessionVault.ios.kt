package com.nuvio.app.core.cloud

/**
 * Pas de persistance tant qu'un coffre de secrets propre à cet OS
 * (DPAPI pour Windows / Keychain pour Apple) n'est pas intégré et vérifié.
 * La connexion en mémoire continue de fonctionner sans exposer le jeton.
 */
internal actual object CloudSessionVault {
    actual fun load(): String? = null
    actual fun save(payload: String) = Unit
    actual fun clear() = Unit
}
