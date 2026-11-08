package com.kovelyx.tv.core.sync

import com.kovelyx.cloud.addons.canonicalAddonManifestUrl
import com.kovelyx.cloud.catalogues.*
import com.kovelyx.tv.core.auth.CloudflareTvAccountClient
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.json.*

/** Share explicitly between cloud profiles; all remote writes use owner checks and CAS. */
internal object CloudflareTvProfileSharing {
    data class Result(val profiles: Int, val addons: Int, val catalogues: Int)
    private val uuid = Regex("[a-fA-F0-9-]{36}")
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val privateField = Regex("api[_-]?key|access[_-]?token|refresh[_-]?token|password|client[_-]?secret|private[_-]?key", RegexOption.IGNORE_CASE)
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
    private fun row(rows: List<JSONObject>, category: String, key: String): JSONObject? =
        rows.firstOrNull { it.optString("category") == category &&
            it.optString("itemKey") == key && !it.optBoolean("deleted") }
    private fun secure(id: String, token: String) =
        check(CloudflareTvAccountClient.account?.id == id &&
            CloudflareTvAccountClient.account?.bearerToken == token) {
            "Le compte a changé pendant le partage"
        }

    private fun noSecrets(node: JsonElement, depth: Int = 0) {
        require(depth <= 64) { "Catalogue trop profond" }
        when (node) {
            is JsonObject -> node.forEach { (key, child) ->
                require(!privateField.matches(key)) { "Champ privé détecté dans un catalogue" }
                noSecrets(child, depth + 1)
            }
            is JsonArray -> node.forEach { noSecrets(it, depth + 1) }
            else -> Unit
        }
    }

    private fun mergeAddons(existing: JSONObject?, origin: JSONObject): JSONObject {
        val before = existing?.optJSONArray("items") ?: JSONArray()
        val source = origin.optJSONArray("items") ?: error("Extensions source illisibles")
        val ordered = JSONArray()
        val seen = mutableSetOf<String>()
        for (items in listOf(before, source)) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: error("Extension invalide")
                val url = item.optString("url")
                require((url.startsWith("https://") || url.startsWith("http://")) &&
                    url.length <= 24000 && item.optString("name").length <= 500)
                if (seen.add(canonicalAddonManifestUrl(url))) ordered.put(item)
            }
        }
        require(ordered.length() <= 100)
        return JSONObject().put("items", ordered)
            .put("defaultsVersion", maxOf(1,
                existing?.optInt("defaultsVersion") ?: 0,
                origin.optInt("defaultsVersion"))).also {
                require(it.toString().length <= 32000) { "Configuration des extensions trop volumineuse" }
            }
    }

    private suspend fun snapshot(
        profile: String, rows: List<JSONObject>, kind: String
    ): Pair<CatalogueHead, String>? {
        val manifest = row(rows, "preferences", "catalogue_$kind") ?: return null
        val info = manifest.optJSONObject("payload") ?: error("Manifeste de catalogue absent")
        val hash = info.optString("sha256")
        val head = CatalogueHead(hash,info.optInt("parts"),info.optInt("utf8Bytes"),manifest.optInt("revision"))
        require(info.optInt("schema") == 1 && hashPattern.matches(hash) &&
            head.parts in 1..CATALOGUE_MAX_PARTS && head.utf8Bytes in 1..CATALOGUE_MAX_BYTES)
        val pieces = CloudflareTvAccountClient.syncRows(profile,kind,hash).map {
            it.optJSONObject("payload") ?: error("Bloc de catalogue illisible")
        }.sortedBy { it.optInt("index") }
        require(pieces.size == head.parts && pieces.withIndex().all { (index, part) ->
            part.optInt("index",-1) == index && part.optString("text").length in 1..CATALOGUE_CHUNK_CHARS
        }) { "Blocs manquants : catalogues du profil conservés" }
        val text = pieces.joinToString("") { it.getString("text") }
        require(text.toByteArray(Charsets.UTF_8).size == head.utf8Bytes && sha(text) == hash) {
            "Catalogue corrompu : partage annulé"
        }
        noSecrets(Json.parseToJsonElement(text))
        return head to text
    }

    suspend fun share(source: String, targets: Set<String>, addons: Boolean, catalogues: Boolean): Result {
        require(addons || catalogues)
        require(uuid.matches(source) && targets.isNotEmpty() &&
            targets.all { uuid.matches(it) && it != source }) { "Sélection de profils invalide" }
        val signed = CloudflareTvAccountClient.account ?: error("Compte Cloudflare non connecté")
        val allowed = CloudflareTvAccountClient.cloudProfiles().map { it.first }.toSet()
        require(source in allowed && targets.all { it in allowed }) { "Profil non autorisé" }
        val sourceRows = CloudflareTvAccountClient.syncRows(source)
        val sourceAddons = if (addons) row(sourceRows,"addons","installed")?.optJSONObject("payload") else null
        val sourceSnapshots = if (catalogues) listOf("collections","layout").mapNotNull { kind ->
            snapshot(source,sourceRows,kind)?.let { kind to it.second }
        } else emptyList()
        require(sourceAddons != null || sourceSnapshots.isNotEmpty()) { "Rien à copier depuis ce profil" }
        var changedProfiles=0
        var changedAddons=0
        var changedCatalogues=0
        for (target in targets.sorted()) {
            secure(signed.id,signed.bearerToken)
            val rows = CloudflareTvAccountClient.syncRows(target)
            val plans = sourceSnapshots.mapNotNull { (kind,src) ->
                val old = snapshot(target,rows,kind)
                val merged = if (old == null) src else
                    mergeInitialCatalogue(kind,old.second,src,preferLocal=false)
                noSecrets(Json.parseToJsonElement(merged))
                require(merged.toByteArray(Charsets.UTF_8).size in 1..CATALOGUE_MAX_BYTES) {
                    "Catalogue fusionné trop volumineux"
                }
                if (sha(merged) == old?.first?.sha256) null else Triple(kind,old?.first,merged)
            }
            val currentAddon = if (sourceAddons != null) row(rows,"addons","installed") else null
            val payload = sourceAddons?.let { mergeAddons(currentAddon?.optJSONObject("payload"),it) }
            val addonChanged = payload != null && payload.toString() != currentAddon?.optJSONObject("payload")?.toString()
            if (plans.isEmpty() && !addonChanged) continue

            for ((kind,head,text) in plans) {
                secure(signed.id,signed.bearerToken)
                val hash = sha(text)
                val pieces = splitCatalogue(text)
                for (piece in pieces) {
                    secure(signed.id,signed.bearerToken)
                    CloudflareTvAccountClient.putCatalogueChunk(target,kind,hash,piece.index,piece.text)
                }
                secure(signed.id,signed.bearerToken)
                CloudflareTvAccountClient.publishCatalogueHead(target,kind,CatalogueHead(
                    hash,pieces.size,text.toByteArray(Charsets.UTF_8).size,head?.revision ?: 0))
                changedCatalogues++
            }
            if (addonChanged) {
                secure(signed.id,signed.bearerToken)
                CloudflareTvAccountClient.writeSync(target,"addons","installed",
                    payload!!,currentAddon?.optInt("revision") ?: 0)
                changedAddons++
            }
            changedProfiles++
        }
        return Result(changedProfiles,changedAddons,changedCatalogues)
    }
}
