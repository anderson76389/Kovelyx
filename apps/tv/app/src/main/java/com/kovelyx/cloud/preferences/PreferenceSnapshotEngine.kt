package com.kovelyx.cloud.preferences

import com.kovelyx.cloud.catalogues.CatalogueStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
private data class PreferenceCheckpoint(val baseline: JsonObject? = null, val pending: Boolean = false, val generation: Long = 0, val receiving: Boolean = false)
data class PreferenceRow(val payload: JsonObject, val revision: Int)

/** One small record per feature. The checkpoint is durable and scoped by owner and cloud profile. */
class PreferenceSnapshotEngine(private val store: CatalogueStateStore) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun key(owner: String, profile: String, feature: String) = "preferences|$owner|$profile|$feature"
    private fun checkpoint(raw: String?) = raw?.let {
        runCatching { json.decodeFromString<PreferenceCheckpoint>(it) }.getOrNull()
    } ?: PreferenceCheckpoint()

    private fun save(key: String, expected: String?, next: String): Boolean =
        if (expected == next) store.load(key) == expected else store.compareAndSave(key, expected, next)

    fun edited(owner: String, profile: String, feature: String) {
        val key = key(owner, profile, feature)
        while (true) {
            val raw = store.load(key)
            if (save(key, raw, json.encodeToString(checkpoint(raw).let { it.copy(pending = true, generation = it.generation + 1) }))) return
        }
    }

    suspend fun synchronize(owner: String, profile: String, feature: String, row: PreferenceRow?,
        current: suspend () -> JsonObject, apply: suspend (JsonObject, JsonObject) -> Boolean,
        publish: suspend (JsonObject, Int) -> Unit, isCurrent: () -> Boolean): Boolean = mutex.withLock {
        check(isCurrent())
        val key = key(owner, profile, feature)
        var raw = store.load(key)
        val saved = checkpoint(raw)
        val local = current()
        check(isCurrent())
        val changed = saved.pending || (!saved.receiving && saved.baseline != null && saved.baseline != local)
        if (row != null && !changed && row.payload != local) {
            if (store.load(key) != raw || !isCurrent() || current() != local) return@withLock false
            val receiving = json.encodeToString(saved.copy(receiving = true))
            if (!save(key, raw, receiving)) return@withLock false
            raw = receiving
            if (!apply(local, row.payload)) return@withLock false
        } else if (row?.payload != local) {
            require(local.toString().length <= 32000) { "Préférences trop volumineuses" }
            // Keep pending across a lost HTTP response or a restart.
            val pending = json.encodeToString(saved.copy(pending = true))
            if (!save(key, raw, pending)) return@withLock false
            publish(local, row?.revision ?: 0)
            check(isCurrent())
            if (current() != local) return@withLock false
            return@withLock save(key, pending, json.encodeToString(PreferenceCheckpoint(local)))
        }
        check(isCurrent())
        return@withLock save(key, raw, json.encodeToString(PreferenceCheckpoint(current())))
    }
}

fun preferenceRow(rows: List<JsonObject>, feature: String): PreferenceRow? = rows.firstOrNull {
    it["category"]?.jsonPrimitive?.content == "profile_settings" &&
        it["itemKey"]?.jsonPrimitive?.content == feature && it["deleted"]?.jsonPrimitive?.booleanOrNull != true
}?.let { PreferenceRow(it["payload"]!!.jsonObject, it["revision"]!!.jsonPrimitive.int) }
