package com.kovelyx.cloud.catalogues

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

const val CATALOGUE_CHUNK_CHARS = 6000
const val CATALOGUE_MAX_BYTES = 4_000_000
const val CATALOGUE_MAX_PARTS = 700

data class CatalogueHead(val sha256: String, val parts: Int, val utf8Bytes: Int, val revision: Int)
data class CatalogueChunk(val index: Int, val text: String)
interface CatalogueStateStore {
    fun load(key: String): String?
    fun compareAndSave(key: String, expected: String?, next: String): Boolean
}
interface CatalogueRemote {
    suspend fun putChunk(profile: String, kind: String, hash: String, chunk: CatalogueChunk)
    suspend fun readChunks(profile: String, kind: String, hash: String): List<CatalogueChunk>
    suspend fun publish(profile: String, kind: String, head: CatalogueHead)
}
@Serializable
private data class CatalogueState(
    val profileUuid: String? = null,
    val generation: Long = 0,
    val dirty: Boolean = false,
    val initialized: Boolean = false,
    val remoteHash: String? = null,
    val pendingHash: String? = null,
    val localHash: String? = null,
)

fun splitCatalogue(text: String): List<CatalogueChunk> {
    val result = mutableListOf<CatalogueChunk>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + CATALOGUE_CHUNK_CHARS, text.length)
        if (end < text.length && text[end - 1].code in 0xD800..0xDBFF && text[end].code in 0xDC00..0xDFFF) end--
        result += CatalogueChunk(result.size, text.substring(start, end))
        start = end
    }
    require(result.size in 1..CATALOGUE_MAX_PARTS) { "Catalogue trop volumineux pour une synchronisation légère." }
    return result
}

fun readCatalogueHead(rows: List<JsonObject>, kind: String): CatalogueHead? {
    val row = rows.firstOrNull { it["category"]?.jsonPrimitive?.content == "preferences" &&
        it["itemKey"]?.jsonPrimitive?.content == "catalogue_$kind" && it["deleted"]?.jsonPrimitive?.booleanOrNull != true } ?: return null
    val payload = row["payload"]?.jsonObject ?: error("Version de catalogue illisible")
    require(payload["schema"]?.jsonPrimitive?.intOrNull == 1)
    return CatalogueHead(payload["sha256"]!!.jsonPrimitive.content,
        payload["parts"]!!.jsonPrimitive.int, payload["utf8Bytes"]!!.jsonPrimitive.int, row["revision"]!!.jsonPrimitive.int)
}

/** Initial migration keeps unique catalogues from each device; subsequent removals stay removals. */
fun mergeInitialCatalogue(kind: String, remote: String, local: String, preferLocal: Boolean): String {
    val remoteJson = Json.parseToJsonElement(remote)
    val localJson = Json.parseToJsonElement(local)
    fun identity(value: JsonElement): String {
        val row = value.jsonObject
        if (kind == "collections") return row["id"]!!.jsonPrimitive.content
        if (row["is_collection"]?.jsonPrimitive?.booleanOrNull == true)
            return "collection:" + row["collection_id"]!!.jsonPrimitive.content
        return listOf("addon_id", "type", "catalog_id").joinToString("|") { row[it]?.jsonPrimitive?.content.orEmpty() }
            .takeIf { it != "||" } ?: row["key"]?.jsonPrimitive?.content.orEmpty()
    }
    fun mergeArrays(a: JsonArray, b: JsonArray): JsonArray {
        val second = b.associateBy(::identity)
        val seen = mutableSetOf<String>()
        return JsonArray((a + b).filter { seen.add(identity(it)) }.map { item ->
            JsonObject(second[identity(item)]?.jsonObject.orEmpty() + item.jsonObject)
        })
    }
    val decorativeSourceKeys = setOf("id", "title", "name")
    fun canonical(value: JsonElement, root: Boolean = true): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.entries.sortedBy { it.key }.mapNotNull { (key, child) ->
            if ((root && key in decorativeSourceKeys) || child == JsonNull || (child is JsonPrimitive && child.isString && child.content.isEmpty())) null
            else key to canonical(child, false)
        }.toMap())
        is JsonArray -> JsonArray(value.map { canonical(it, false) })
        else -> value
    }
    fun sources(folder: JsonObject): List<JsonElement> {
        val modern = folder["sources"]?.jsonArray.orEmpty()
        return if (modern.isNotEmpty()) modern else folder["catalogSources"]?.jsonArray.orEmpty().map {
            JsonObject(it.jsonObject + ("provider" to JsonPrimitive("addon")))
        }
    }
    fun mergeFolders(a: JsonArray, b: JsonArray): JsonArray {
        val secondary = b.associateBy { it.jsonObject["id"]!!.jsonPrimitive.content }
        val seen = mutableSetOf<String>()
        return JsonArray((a + b).filter { seen.add(it.jsonObject["id"]!!.jsonPrimitive.content) }.map { row ->
            val other = secondary[row.jsonObject["id"]!!.jsonPrimitive.content]?.jsonObject ?: JsonObject(emptyMap())
            val merged = other + row.jsonObject
            val uniqueSources = (sources(row.jsonObject) + sources(other)).distinctBy {
                val source = it.jsonObject
                val provider = source["provider"]?.jsonPrimitive?.content?.lowercase()
                // genre is an addon parameter, but only a display label on TMDB/Trakt imports.
                canonical(if (provider == "tmdb" || provider == "trakt") JsonObject(source - "genre") else source).toString()
            }
            JsonObject(merged - "catalogSources" + ("sources" to JsonArray(uniqueSources)))
        })
    }
    val primary = if (preferLocal) localJson else remoteJson
    val secondary = if (preferLocal) remoteJson else localJson
    if (kind == "collections") {
        val secondaryById = secondary.jsonArray.associateBy(::identity)
        return JsonArray(mergeArrays(primary.jsonArray, secondary.jsonArray).map { row ->
            val other = secondaryById[identity(row)]?.jsonObject
            JsonObject(row.jsonObject + ("folders" to mergeFolders(row.jsonObject["folders"]?.jsonArray ?: JsonArray(emptyList()),
                other?.get("folders")?.jsonArray ?: JsonArray(emptyList()))))
        }).toString()
    }
    val items = mergeArrays(primary.jsonObject["items"]?.jsonArray ?: JsonArray(emptyList()),
        secondary.jsonObject["items"]?.jsonArray ?: JsonArray(emptyList()))
    return JsonObject(secondary.jsonObject + primary.jsonObject + ("items" to JsonArray(items.mapIndexed { index, item ->
        JsonObject(item.jsonObject + ("order" to JsonPrimitive(index)))
    }))).toString()
}

class CatalogueSnapshotEngine(
    private val store: CatalogueStateStore,
    private val digest: (ByteArray) -> String,
    private val remote: CatalogueRemote,
) {
    private val mutex = Mutex()
    private val credentialKeyPattern = Regex("api[_-]?key|access[_-]?token|refresh[_-]?token|password|client[_-]?secret|private[_-]?key", RegexOption.IGNORE_CASE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private data class Entry(val raw: String?, val state: CatalogueState)
    private fun key(owner: String, localProfile: Int, kind: String) = digest("catalogue|$owner|$localProfile|$kind".encodeToByteArray())
    private fun load(key: String): Entry {
        val raw = store.load(key)
        return Entry(raw, raw?.let { runCatching { json.decodeFromString<CatalogueState>(it) }.getOrNull() } ?: CatalogueState())
    }
    private fun save(key: String, before: Entry, next: CatalogueState) = store.compareAndSave(key, before.raw, json.encodeToString(next))

    fun edited(owner: String, localProfile: Int, profileUuid: String?, kind: String) {
        require(kind in setOf("collections", "layout"))
        val key = key(owner, localProfile, kind)
        while (true) {
            val before = load(key)
            val state = if (profileUuid != null && before.state.profileUuid != null && before.state.profileUuid != profileUuid)
                CatalogueState(profileUuid = profileUuid) else before.state
            if (save(key, before, state.copy(profileUuid = profileUuid ?: state.profileUuid, generation = state.generation + 1, dirty = true))) return
        }
    }

    suspend fun synchronize(
        owner: String, localProfile: Int, profileUuid: String, kind: String, head: CatalogueHead?,
        current: suspend () -> String, apply: suspend (expected: String, next: String) -> Boolean, isCurrent: () -> Boolean,
    ): Boolean = mutex.withLock {
        require(kind in setOf("collections", "layout"))
        fun valid() = check(isCurrent()) { "Compte ou profil modifié pendant la synchronisation" }
        valid()
        val key = key(owner, localProfile, kind)
        var entry = load(key)
        if (entry.state.profileUuid != null && entry.state.profileUuid != profileUuid) {
            if (!save(key, entry, CatalogueState(profileUuid = profileUuid))) return@withLock false
            entry = load(key)
        }
        val state = entry.state
        val initialized = state.initialized || (head != null && state.pendingHash == head.sha256)
        val local = current()
        valid()
        if (!state.dirty && initialized && state.remoteHash == head?.sha256 &&
            state.localHash == digest(local.encodeToByteArray())) return@withLock true

        suspend fun receive(remoteHead: CatalogueHead): String {
            require(remoteHead.sha256.matches(Regex("[a-f0-9]{64}")) && remoteHead.parts in 1..CATALOGUE_MAX_PARTS &&
                remoteHead.utf8Bytes in 1..CATALOGUE_MAX_BYTES)
            val parts = remote.readChunks(profileUuid, kind, remoteHead.sha256).sortedBy { it.index }
            require(parts.size == remoteHead.parts && parts.withIndex().all { (i, part) -> part.index == i && part.text.length in 1..CATALOGUE_CHUNK_CHARS })
            val text = parts.joinToString("") { it.text }
            val bytes = text.encodeToByteArray()
            require(bytes.size == remoteHead.utf8Bytes && digest(bytes) == remoteHead.sha256) { "Catalogue incomplet ; données locales conservées" }
            Json.parseToJsonElement(text)
            return text
        }

        if (head != null && (!initialized || !state.dirty)) {
            val received = receive(head)
            valid()
            if (load(key).raw != entry.raw || current() != local) return@withLock false
            val merged = if (!initialized) mergeInitialCatalogue(kind, received, local, state.dirty) else received
            require(merged.encodeToByteArray().size <= CATALOGUE_MAX_BYTES) { "Ensemble des catalogues supérieur à 4 Mo ; données locales conservées" }
            if (Json.parseToJsonElement(merged) != Json.parseToJsonElement(local) && !apply(local, merged)) return@withLock false
            valid()
            val pending = Json.parseToJsonElement(merged) != Json.parseToJsonElement(received)
            return@withLock save(key, entry, state.copy(profileUuid = profileUuid, initialized = true,
                remoteHash = head.sha256, dirty = pending, pendingHash = null,
                localHash = digest(current().encodeToByteArray()))) && !pending
        }

        var publication = local
        if (kind == "layout" && head != null && state.dirty) {
            val previous = receive(head)
            valid()
            if (load(key).raw != entry.raw || current() != local) return@withLock false
            publication = mergeInitialCatalogue(kind, previous, local, preferLocal = true)
        }
        val bytes = publication.encodeToByteArray()
        require(bytes.size in 1..CATALOGUE_MAX_BYTES) { "Catalogue supérieur à 4 Mo ; synchronisation différée pour limiter le cloud." }
        fun validate(value: JsonElement, depth: Int = 0) {
            require(depth <= 64) { "Structure de catalogue trop profonde" }
            when (value) {
                is JsonObject -> value.forEach { (name, child) ->
                    require(!name.matches(credentialKeyPattern)) {
                        "Un identifiant privé ne peut pas être synchronisé dans un catalogue"
                    }
                    validate(child, depth + 1)
                }
                is JsonArray -> value.forEach { validate(it, depth + 1) }
                else -> Unit
            }
        }
        validate(Json.parseToJsonElement(publication))
        val hash = digest(bytes)
        if (hash != head?.sha256) {
            // Persist the attempted hash before network I/O. If publication succeeds
            // but its response is lost, its own old snapshot is never unioned back
            // into a newer local edit during initial migration.
            if (!save(key, entry, state.copy(profileUuid = profileUuid, pendingHash = hash, dirty = true))) return@withLock false
            entry = load(key)
            val parts = splitCatalogue(publication)
            for (part in parts) { valid(); remote.putChunk(profileUuid, kind, hash, part) }
            valid()
            remote.publish(profileUuid, kind, CatalogueHead(hash, parts.size, bytes.size, head?.revision ?: 0))
        }
        valid()
        while (true) {
            val latest = load(key)
            if (latest.state.profileUuid != null && latest.state.profileUuid != profileUuid) return@withLock false
            val unchanged = latest.raw == entry.raw && current() == local
            valid()
            if (save(key, latest, latest.state.copy(profileUuid = profileUuid, initialized = true,
                remoteHash = hash, pendingHash = null, dirty = !unchanged,
                localHash = if (unchanged) digest(local.encodeToByteArray()) else latest.state.localHash))) return@withLock unchanged
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }
}
