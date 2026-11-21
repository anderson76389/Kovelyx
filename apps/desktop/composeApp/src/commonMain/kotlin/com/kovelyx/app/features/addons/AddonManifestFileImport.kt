package com.kovelyx.app.features.addons

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Read-only import of installable manifest URLs. A local Stremio manifest is not
 * an addon server: its catalog/meta/stream routes need an HTTPS origin.
 * Never turn untrusted JSON or a website ZIP into executable code.
 */
internal fun parseAddonManifestUrlsFile(text: String): List<String> {
    require(text.length <= 16 * 1024 * 1024) { "Fichier trop volumineux (16 Mo maximum)." }
    val source = text.trim().removePrefix("\uFEFF")
    require(source.isNotBlank()) { "Le fichier JSON est vide." }
    require(!source.startsWith("PK")) { "Une archive ZIP de site web n'est pas un addon. Importez un JSON de liens manifests." }
    val root = try {
        Json.parseToJsonElement(source)
    } catch (_: Exception) {
        throw IllegalArgumentException("Ce fichier n'est pas un JSON valide. Choisissez un export d'extensions.")
    }
    if (root is JsonObject && root["resources"] != null && root["id"] != null && root["manifestUrl"] == null) {
        throw IllegalArgumentException("Ce JSON décrit un manifest Stremio, mais pas son adresse. Hébergez l'addon et importez son URL HTTPS /manifest.json.")
    }
    val records: List<JsonElement> = when (root) {
        is JsonArray -> root.toList()
        is JsonObject -> {
            val data = root["addons"] ?: root["addonUrls"] ?: root["manifestUrls"] ?: root["extensions"]
            when (data) {
                is JsonArray -> data.toList()
                null -> listOf(root)
                else -> throw IllegalArgumentException("La liste des extensions doit être un tableau JSON.")
            }
        }
        else -> throw IllegalArgumentException("Format de fichier d'extensions non reconnu.")
    }
    require(records.isNotEmpty()) { "Aucune extension présente dans ce fichier." }
    require(records.size <= 25) { "Import limité à 25 extensions par fichier." }
    val result = linkedSetOf<String>()
    for (item in records) {
        val raw = when (item) {
            is JsonPrimitive -> item.content
            is JsonObject -> {
                if (item["folders"] != null || item["catalogSources"] != null) {
                    throw IllegalArgumentException("Ce fichier contient des collections. Utilisez Collections → Importer JSON.")
                }
                (item["manifestUrl"] ?: item["manifest_url"] ?: item["url"] ?: item["manifest"])?.let {
                    (it as? JsonPrimitive)?.content
                }
            }
            else -> null
        }?.trim()
        require(!raw.isNullOrBlank()) {
            "Format non compatible : chaque extension doit fournir manifestUrl, manifest_url ou url."
        }
        require(raw.length <= 2048) { "Une adresse d'extension est trop longue." }
        val url = try { Url(raw) } catch (_: Exception) {
            throw IllegalArgumentException("Adresse d'extension invalide.")
        }
        require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() &&
            url.user.isNullOrBlank() && url.password.isNullOrBlank() &&
            url.encodedPath.endsWith("/manifest.json") && url.fragment.isEmpty()) {
            "Chaque extension doit avoir une URL HTTPS se terminant par /manifest.json."
        }
        result.add(raw)
    }
    return result.toList()
}
