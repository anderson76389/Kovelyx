package com.nuvio.app.features.library

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class CloudLibraryRow(
    val revision: Int,
    val deleted: Boolean,
    val item: LibraryItem?,
)

internal object CloudflareLibraryCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(rows: List<JsonObject>): Map<String, CloudLibraryRow> =
        rows.associate { row ->
            val key = row["itemKey"]?.jsonPrimitive?.content
                ?: error("Clé distante manquante")
            require(key.isNotBlank() && key.length <= 200) { "Clé distante invalide" }
            val revision = row["revision"]?.jsonPrimitive?.intOrNull
                ?: error("Révision D1 manquante")
            require(revision > 0) { "Révision D1 invalide" }
            val deleted = row["deleted"]?.jsonPrimitive?.booleanOrNull
                ?: error("Suppression D1 invalide")
            val item = if (deleted) null else {
                val data = row["payload"]?.jsonObject?.get("item")
                    ?: error("Élément bibliothèque manquant")
                json.decodeFromJsonElement<LibraryItem>(data).also { decoded ->
                    require(libraryItemKey(decoded.id,decoded.type) == key) {
                        "Identifiant d'élément différent du contenu"
                    }
                }
            }
            key to CloudLibraryRow(revision,deleted,item)
        }

    /**
     * Envoyer uniquement des métadonnées non sensibles. Pas de liens de
     * lecteurs, d'addons privés, de jetons ou de paramètres fournisseur.
     */
    fun payload(item: LibraryItem): JsonObject {
        fun safePoster(url: String?): String? = url?.takeIf {
            it.length <= 1024 && it.startsWith("https://image.tmdb.org/t/p/") &&
                '?' !in it && '#' !in it
        }
        val data = buildJsonObject {
            put("id", item.id)
            put("type", item.type)
            put("name", item.name.take(300))
            put("savedAtEpochMs", item.savedAtEpochMs)
            safePoster(item.poster)?.let { put("poster", it) }
            safePoster(item.banner)?.let { put("banner", it) }
        }
        return buildJsonObject { put("item",data) }
    }
}
