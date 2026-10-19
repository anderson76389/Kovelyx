package com.nuvio.tv.core.sync

/**
 * Association prudente entre les profils numériques historiques de la TV et les
 * profils UUID du compte Kovelyx. Jamais d'association par ordre de liste.
 * Dans le seul cas 1 local / 1 cloud, la correspondance est non ambiguë.
 * Avec plusieurs profils, seules les dénominations uniques sont associées.
 */
internal object CloudflareTvProfileBinding {
    data class Local(val id: Int, val name: String)
    data class Remote(val id: String, val name: String)

    fun resolve(locals: List<Local>, remotes: List<Remote>): Map<Int, String> {
        if (locals.isEmpty() || remotes.isEmpty()) return emptyMap()
        if (locals.map { it.id }.toSet().size != locals.size) return emptyMap()
        if (remotes.map { it.id }.toSet().size != remotes.size) return emptyMap()
        if (remotes.any { !it.id.matches(Regex("[a-fA-F0-9-]{36}")) }) return emptyMap()
        if (locals.size == 1 && remotes.size == 1)
            return mapOf(locals.first().id to remotes.first().id)
        fun normalize(name: String) = name.trim().lowercase()
        return locals.mapNotNull { local ->
            val localName = normalize(local.name)
            if (localName.isBlank()) return@mapNotNull null
            if (locals.count { normalize(it.name) == localName } != 1) return@mapNotNull null
            val candidates = remotes.filter { normalize(it.name) == localName }
            if (candidates.size != 1) return@mapNotNull null
            local.id to candidates.single().id
        }.toMap()
    }
}
