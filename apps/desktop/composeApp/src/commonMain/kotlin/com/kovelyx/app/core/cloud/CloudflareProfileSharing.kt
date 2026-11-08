package com.kovelyx.app.core.cloud

import com.kovelyx.cloud.addons.canonicalAddonManifestUrl
import com.kovelyx.cloud.catalogues.CATALOGUE_MAX_BYTES
import com.kovelyx.cloud.catalogues.CATALOGUE_MAX_PARTS
import com.kovelyx.cloud.catalogues.CatalogueHead
import com.kovelyx.cloud.catalogues.mergeInitialCatalogue
import com.kovelyx.cloud.catalogues.readCatalogueHead
import com.kovelyx.cloud.catalogues.splitCatalogue
import com.kovelyx.app.features.profiles.ProfilePinCrypto
import kotlinx.serialization.json.*

/**
 * One-shot, explicit sharing to other profiles of the SAME authenticated D1 account.
 * Reads/writes ONLY the existing account's own /v1 endpoints. Never changes a local profile
 * or copies library, PINs, history, playback positions, passwords, or profile pictures.
 */
object CloudflareProfileSharing {
    data class CloudProfile(val id: String, val name: String)
    data class Result(val profilesUpdated: Int, val addonsUpdated: Int, val cataloguesUpdated: Int)

    private val uuid = Regex("[a-fA-F0-9-]{36}")
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val forbidden = Regex("api[_-]?key|access[_-]?token|refresh[_-]?token|password|client[_-]?secret|private[_-]?key", RegexOption.IGNORE_CASE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun profiles(): List<CloudProfile> =
        CloudflareAccountClient.accountProfiles()["profiles"]?.jsonArray.orEmpty().map {
            val row = it.jsonObject
            CloudProfile(row["id"]!!.jsonPrimitive.content, row["displayName"]!!.jsonPrimitive.content)
        }.filter { uuid.matches(it.id) }

    private fun find(rows: List<JsonObject>, category: String, key: String): JsonObject? =
        rows.firstOrNull { row ->
            row["category"]?.jsonPrimitive?.content == category &&
                row["itemKey"]?.jsonPrimitive?.content == key &&
                row["deleted"]?.jsonPrimitive?.booleanOrNull != true
        }

    private fun revision(row: JsonObject?): Int = row?.get("revision")?.jsonPrimitive?.intOrNull ?: 0

    private fun noSecrets(node: JsonElement, depth: Int = 0) {
        require(depth <= 64) { "Catalogue trop profond" }
        when (node) {
            is JsonObject -> node.forEach { (key, child) ->
                require(!forbidden.matches(key)) { "Champ privé détecté : partage annulé" }
                noSecrets(child, depth + 1)
            }
            is JsonArray -> node.forEach { noSecrets(it, depth + 1) }
            else -> Unit
        }
    }

    /** The recipient's configured URLs and order always win. Unique extra URLs are appended. */
    fun mergeAddons(existing: JsonObject?, source: JsonObject): JsonObject {
        val recipient = existing?.get("items")?.jsonArray.orEmpty()
        val origin = source["items"]?.jsonArray ?: error("Extensions du profil source illisibles")
        val ordered = mutableListOf<JsonElement>()
        val known = mutableSetOf<String>()
        for (item in recipient + origin) {
            val row = item.jsonObject
            val url = row["url"]?.jsonPrimitive?.content.orEmpty()
            require(url.startsWith("https://") || url.startsWith("http://")) { "URL d'extension invalide" }
            require(url.length <= 24_000 && row["name"]?.jsonPrimitive?.content.orEmpty().length <= 500)
            if (known.add(canonicalAddonManifestUrl(url))) ordered.add(item)
        }
        require(ordered.size <= 100) { "Maximum 100 extensions sur un profil" }
        return buildJsonObject {
            put("defaultsVersion", maxOf(1,
                existing?.get("defaultsVersion")?.jsonPrimitive?.intOrNull ?: 0,
                source["defaultsVersion"]?.jsonPrimitive?.intOrNull ?: 0))
            put("items", JsonArray(ordered))
        }.also { require(it.toString().length <= 32000) { "Liste d'extensions trop volumineuse" } }
    }

    private suspend fun snapshot(profile: String, rows: List<JsonObject>, kind: String): Pair<CatalogueHead, String>? {
        val head = readCatalogueHead(rows, kind) ?: return null
        require(hashPattern.matches(head.sha256) && head.parts in 1..CATALOGUE_MAX_PARTS &&
            head.utf8Bytes in 1..CATALOGUE_MAX_BYTES) { "Catalogue cloud invalide" }
        val pieces = CloudflareAccountClient.synchronizeAll(profile, kind, head.sha256)
            .map { it["payload"]!!.jsonObject }.sortedBy { it["index"]!!.jsonPrimitive.int }
        require(pieces.size == head.parts && pieces.withIndex().all { (index, row) ->
            row["index"]?.jsonPrimitive?.int == index &&
                row["text"]?.jsonPrimitive?.content.orEmpty().length in 1..6000
        }) { "Catalogue distant incomplet : aucun changement" }
        val text = pieces.joinToString("") { it["text"]!!.jsonPrimitive.content }
        val bytes = text.encodeToByteArray()
        require(bytes.size == head.utf8Bytes && ProfilePinCrypto.sha256Hex(text) == head.sha256) {
            "Empreinte du catalogue invalide : aucun changement"
        }
        noSecrets(json.parseToJsonElement(text))
        return head to text
    }

    suspend fun copy(
        sourceId: String, recipientIds: Set<String>,
        extensions: Boolean, catalogues: Boolean,
    ): Result {
        require(extensions || catalogues) { "Choisissez au moins un type de contenu" }
        require(uuid.matches(sourceId) && recipientIds.isNotEmpty() &&
            recipientIds.all { uuid.matches(it) && it != sourceId }) { "Profils non valides" }
        val session = CloudflareAccountClient.currentSession ?: error("Compte Kovelyx non connecté")
        val all = profiles().map { it.id }.toSet()
        require(sourceId in all && recipientIds.all { it in all }) { "Profils inconnus dans ce compte" }

        fun ensureAccount() {
            check(CloudflareAccountClient.currentSession?.userId == session.userId &&
                CloudflareAccountClient.currentSession?.bearerToken == session.bearerToken) {
                "Compte changé pendant le partage, opération interrompue"
            }
        }
        ensureAccount()
        val originRows = CloudflareAccountClient.synchronizeAll(sourceId)
        val originAddons = if (extensions) find(originRows, "addons", "installed")?.get("payload")?.jsonObject else null
        // Fully verify all source snapshots BEFORE starting any remote write.
        val originSnapshots = if (catalogues) listOf("collections", "layout").mapNotNull { kind ->
            snapshot(sourceId, originRows, kind)?.let { kind to it.second }
        } else emptyList()
        if (extensions && originAddons == null && originSnapshots.isEmpty())
            error("Aucune extension ni aucun catalogue à partager depuis ce profil")
        if (!extensions && originSnapshots.isEmpty()) error("Aucun catalogue distant sur ce profil")
        var changedProfiles = 0
        var changedAddons = 0
        var changedCatalogues = 0
        for (recipientId in recipientIds.sorted()) {
            ensureAccount()
            val targetRows = CloudflareAccountClient.synchronizeAll(recipientId)
            // Plan merges up front; stale D1 revisions are rejected at publication (HTTP 409).
            val plans = mutableListOf<Triple<String, CatalogueHead?, String>>()
            for ((kind, sourceText) in originSnapshots) {
                val previous = snapshot(recipientId, targetRows, kind)
                val merged = if (previous == null) sourceText else
                    mergeInitialCatalogue(kind, previous.second, sourceText, preferLocal = false)
                noSecrets(json.parseToJsonElement(merged))
                require(merged.encodeToByteArray().size in 1..CATALOGUE_MAX_BYTES) {
                    "Catalogue fusionné supérieur à 4 Mo : profil destinataire conservé"
                }
                if (ProfilePinCrypto.sha256Hex(merged) != previous?.first?.sha256)
                    plans.add(Triple(kind, previous?.first, merged))
            }
            val currentAddon = if (extensions && originAddons != null)
                find(targetRows, "addons", "installed") else null
            val updatedAddon = if (originAddons != null)
                mergeAddons(currentAddon?.get("payload")?.jsonObject, originAddons) else null
            val addonsChanged = updatedAddon != null && updatedAddon != currentAddon?.get("payload")
            if (plans.isEmpty() && !addonsChanged) continue
            // Publish verified catalogues atomically per kind. No replacement of older snapshots
            // until every chunk is on the server. Then publish addon changes with optimistic CAS.
            for ((kind, head, text) in plans) {
                ensureAccount()
                val hash = ProfilePinCrypto.sha256Hex(text)
                val parts = splitCatalogue(text)
                for (part in parts) {
                    ensureAccount()
                    CloudflareAccountClient.putCatalogueChunk(recipientId, kind, hash, part.index, part.text)
                }
                ensureAccount()
                CloudflareAccountClient.publishCatalogueHead(recipientId, kind,
                    CatalogueHead(hash, parts.size, text.encodeToByteArray().size, head?.revision ?: 0))
                changedCatalogues++
            }
            if (addonsChanged) {
                ensureAccount()
                CloudflareAccountClient.writeSync(recipientId, "addons", "installed",
                    updatedAddon!!, revision(currentAddon))
                changedAddons++
            }
            changedProfiles++
        }
        return Result(changedProfiles, changedAddons, changedCatalogues)
    }
}
