package com.kovelyx.cloud.addons

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class CloudAddon(val url: String, val name: String = "", val enabled: Boolean = true)
@Serializable
private data class AddonCheckpoint(val items: List<CloudAddon>, val pending: Boolean = false)
data class CloudAddonRow(val revision: Int, val items: List<CloudAddon>)

/** One revisioned ordered list per owned profile. Empty lists carry removals too. */
class CloudAddonEngine(private val load: (String) -> String?, private val save: (String, String) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun synchronize(key: String, local: List<CloudAddon>, remote: CloudAddonRow?,
        write: suspend (List<CloudAddon>, Int) -> Unit,
        apply: suspend (List<CloudAddon>) -> Boolean) {
        require(local.size <= 100) { "100 extensions maximum pour la synchronisation" }
        require(local.all { (it.url.startsWith("https://") || it.url.startsWith("http://")) && it.url.length <= 6000 && it.name.length <= 500 })
        val previous = load(key)?.let { json.decodeFromString<AddonCheckpoint>(it) }
        val dirty = previous?.let { it.pending || it.items != local } ?: (remote == null)
        val selected = if (dirty) local else remote?.items ?: local
        if (dirty) {
            // Persist intent before any request: a failed first import remains pending after restart.
            save(key, json.encodeToString(AddonCheckpoint(local, true)))
            write(local, remote?.revision ?: 0)
        }
        // A profile/account switch or edit during HTTP must not be overwritten or acknowledged.
        if (apply(selected)) save(key, json.encodeToString(AddonCheckpoint(selected)))
    }
}
