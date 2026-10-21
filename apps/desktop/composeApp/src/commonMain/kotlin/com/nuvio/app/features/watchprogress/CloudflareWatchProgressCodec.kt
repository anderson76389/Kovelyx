package com.nuvio.app.features.watchprogress

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Tombstones D1 conservés après fermeture pour éviter de ressusciter une suppression hors ligne. */
internal const val CLOUD_DELETED_PROGRESS_PREFIX = "cloud_deleted:"

/** Métadonnées minimales, jamais les URL privées, cookies ou infos de lecteur. */
internal data class CloudProgressRow(val revision: Int, val deleted: Boolean, val entry: WatchProgressEntry?)

internal object CloudflareWatchProgressCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(rows: List<JsonObject>): Map<String, CloudProgressRow> =
        rows.associate { row ->
            val key = row["itemKey"]?.jsonPrimitive?.content
                ?: error("Clé de progression manquante")
            require(key.isNotBlank() && key.length <= 200)
            val revision = row["revision"]?.jsonPrimitive?.intOrNull
                ?: error("Révision D1 manquante")
            require(revision > 0)
            val deleted = row["deleted"]?.jsonPrimitive?.booleanOrNull
                ?: error("État de suppression D1 invalide")
            val entry = if (deleted) null else {
                val data = row["payload"]?.jsonObject?.get("entry")
                    ?: error("Progression D1 manquante")
                json.decodeFromJsonElement<WatchProgressEntry>(data).also { progress ->
                    require(progress.resolvedProgressKey() == key) {
                        "Identité de progression distante incohérente"
                    }
                    require(progress.lastPositionMs >= 0 && progress.durationMs >= 0 &&
                        progress.lastUpdatedEpochMs >= 0)
                }
            }
            key to CloudProgressRow(revision, deleted, entry)
        }

    private fun safeTmdbImage(url: String?): String? = url?.takeIf {
        it.length <= 1024 && it.startsWith("https://image.tmdb.org/t/p/") &&
            '?' !in it && '#' !in it
    }

    fun payload(entry: WatchProgressEntry): JsonObject {
        // Ne synchroniser aucune URL de flux/addon ni information fournisseur
        // susceptible de contenir une clé personnelle ou un token temporaire.
        val public = entry.copy(
            title = entry.title.take(300),
            logo = safeTmdbImage(entry.logo),
            poster = safeTmdbImage(entry.poster),
            background = safeTmdbImage(entry.background),
            episodeTitle = entry.episodeTitle?.take(300),
            episodeThumbnail = safeTmdbImage(entry.episodeThumbnail),
            providerName = null, providerAddonId = null,
            lastStreamTitle = null, lastStreamSubtitle = null,
            pauseDescription = null, lastSourceUrl = null,
            source = WatchProgressSourceLocal,
            trackingProviderId = null, trackingProviderItemId = null,
            trackingSourceUrl = null
        )
        return buildJsonObject { put("entry", json.encodeToJsonElement(public)) }
    }
}
