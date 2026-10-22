package com.nuvio.tv.ui.screens.player

/**
 * Protection du lecteur TV contre deux défauts connus des AAR Media3 historiques :
 * - chemin localhost zéro copie renvoyant HTTP 400 au lieu de 404 ;
 * - allocateur natif retenant les buffers libérés après reset().
 *
 * Les optimisations réseau, tailles de cache et réglages du lecteur sont conservés,
 * mais le chemin natif à risque reste désactivé tant que les AAR ne sont pas reconstruits.
 */
internal object TvMedia3SafetyPolicy {
    const val allowNativeAllocation: Boolean = false

    fun useSafeLoopbackHttp(scheme: String?, host: String?): Boolean {
        if (!scheme.equals("http", ignoreCase = true) &&
            !scheme.equals("https", ignoreCase = true)) return false
        val normalized = host?.trim('[', ']')?.lowercase() ?: return false
        // Couvrir toutes les variantes loopback, sans résolution DNS.
        if (normalized == "localhost" || normalized == "::1" ||
            normalized == "0:0:0:0:0:0:0:1") return true
        val octets = normalized.split('.')
        return octets.size == 4 && octets[0] == "127" &&
            octets.drop(1).all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) &&
                    (part.toIntOrNull()?.let { it in 0..255 } == true)
            }
    }
}
