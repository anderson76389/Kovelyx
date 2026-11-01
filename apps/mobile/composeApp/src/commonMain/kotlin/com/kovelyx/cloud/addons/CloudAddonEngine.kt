package com.kovelyx.cloud.addons

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class CloudAddon(val url: String, val name: String = "", val enabled: Boolean = true)
@Serializable
private data class AddonCheckpoint(val items: List<CloudAddon>, val pending: Boolean = false, val initialized: Boolean = true)
data class CloudAddonRow(val revision: Int, val items: List<CloudAddon>, val defaultsVersion: Int = 0)

/** One revisioned ordered list per owned profile. Empty lists carry removals too. */
class CloudAddonEngine(private val load: (String) -> String?, private val save: (String, String) -> Unit, private val policy: AddonDefaultsPolicy? = null) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun edited(key: String, items: List<CloudAddon>) {
        val previous = load(key)?.let { json.decodeFromString<AddonCheckpoint>(it) }
        save(key, json.encodeToString(previous?.copy(pending = true) ?: AddonCheckpoint(items, true, false)))
    }
    suspend fun synchronize(key: String, local: List<CloudAddon>, remote: CloudAddonRow?,
        write: suspend (List<CloudAddon>, Int) -> Unit,
        apply: suspend (List<CloudAddon>) -> Boolean): Boolean {
        require(local.size <= 100) { "100 extensions maximum pour la synchronisation" }
        require(local.all { (it.url.startsWith("https://") || it.url.startsWith("http://")) && it.url.length <= 24_000 && it.name.length <= 500 })
        val previous = load(key)?.let { json.decodeFromString<AddonCheckpoint>(it) }
        val dirty = previous?.let { it.pending || it.items != local } ?: (remote == null)
        val initialized = previous?.initialized == true || (previous != null && remote != null && previous.items == remote.items)
        val initial = if (dirty && !initialized && remote != null) {
            // First device import preserves configured providers already present on another device.
            (local + remote.items).distinctBy { canonicalAddonManifestUrl(it.url) }
        } else if (dirty) local else remote?.items ?: local
        val needsDefaults = policy != null && (remote?.defaultsVersion ?: 0) < ADDON_DEFAULTS_VERSION
        val selected = when {
            needsDefaults -> policy!!.seed(initial)
            policy != null -> policy.clean(initial)
            else -> initial
        }
        require(selected.size <= 100)
        require(selected.all { (it.url.startsWith("https://") || it.url.startsWith("http://")) && it.url.length <= 24_000 && it.name.length <= 500 })
        val rewrite = dirty || needsDefaults || selected != initial
        if (rewrite) {
            // A remote migration must be durable locally before publication. If its
            // HTTP response is lost, retrying cannot replace remote addons with an old local list.
            if (selected != local && !apply(selected)) return false
            save(key, json.encodeToString(AddonCheckpoint(selected, true, initialized)))
            write(selected, remote?.revision ?: 0)
        }
        // A profile/account switch or edit during HTTP must not be overwritten or acknowledged.
        if (!apply(selected)) return false
        save(key, json.encodeToString(AddonCheckpoint(selected)))
        return true
    }
}
