package com.kovelyx.app.core.cloud

import com.kovelyx.app.features.addons.httpRequestRaw
import com.kovelyx.app.core.auth.CloudflareCredentialStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlin.time.TimeMark
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * API Cloudflare D1 / Better Auth Kovelyx. Ne pas injecter une URL workers.dev
 * dans le client fournisseur historique : les protocoles sont incompatibles.
 *
 * Sessions sauvegardées uniquement avec les mécanismes sécurisés de l'OS
 * (Android Keystore / Windows DPAPI), vérifiées auprès du Worker à chaque lancement.
 */
object CloudflareAccountClient {
    data class Session(val userId: String, val email: String, val bearerToken: String)
    private val json = Json { ignoreUnknownKeys = true }
    private var session: Session? = null

    val enabled: Boolean get() = CloudflareConfig.URL.startsWith("https://")
    val currentSession: Session? get() = session

    private fun base(): String {
        check(enabled) { "Kovelyx Cloudflare non configuré" }
        return CloudflareConfig.URL.trimEnd('/')
    }

    private suspend fun request(path: String, body: String, authorization: String? = null, method: String = "POST"): Pair<String, Map<String,String>> {
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            // Better Auth rejects native sign-in/sign-out without a trusted Origin.
            // Use only our configured HTTPS Worker origin, never a Nuvio domain.
            "Origin" to base(),
            "User-Agent" to "Kovelyx-Client"
        )
        if (!authorization.isNullOrBlank()) headers["Authorization"] = "Bearer $authorization"
        val response = httpRequestRaw(
            method = method, url = base() + path, headers = headers, body = body,
            followRedirects = false, maxResponseBodyBytes = 131072
        )
        if (response.status !in 200..299) {
            val description = runCatching {
                json.parseToJsonElement(response.body).jsonObject["message"]?.jsonPrimitive?.content
            }.getOrNull().orEmpty()
            error("Kovelyx Cloud : HTTP ${response.status}" + if (description.isNotBlank()) " — $description" else "")
        }
        return response.body to response.headers
    }

    /** Restore encrypted credentials, never treating local storage as proof of identity. */
    suspend fun restoreSession(): Session? {
        if (!enabled) return null
        val raw = runCatching { CloudflareCredentialStore.load() }.getOrNull() ?: return null
        val saved = runCatching {
            val data = json.parseToJsonElement(raw).jsonObject
            Session(
                userId = data["id"]?.jsonPrimitive?.content.orEmpty(),
                email = data["email"]?.jsonPrimitive?.content.orEmpty(),
                bearerToken = data["bearer"]?.jsonPrimitive?.content.orEmpty()
            )
        }.getOrNull()
        if (saved == null || saved.userId.isBlank() || saved.email.isBlank() ||
            saved.bearerToken.length < 16) {
            CloudflareCredentialStore.clear()
            return null
        }
        return try {
            val response = httpRequestRaw(
                method = "GET", url = base() + "/v1/me",
                headers = mapOf("Authorization" to "Bearer ${saved.bearerToken}"),
                body = "", followRedirects = false, maxResponseBodyBytes = 131072
            )
            if (response.status == 401 || response.status == 403) {
                CloudflareCredentialStore.clear()
                return null
            }
            if (response.status != 200) return null
            val me = json.parseToJsonElement(response.body).jsonObject
            val id = me["id"]?.jsonPrimitive?.content
            val email = me["email"]?.jsonPrimitive?.content
            if (id != saved.userId || email != saved.email) {
                CloudflareCredentialStore.clear()
                return null
            }
            if (session == null) session = saved
            session
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve encrypted credentials on transient connection failures.
            null
        }
    }

    private fun persistSession(account: Session) {
        val serialized = buildJsonObject {
            put("id", account.userId)
            put("email", account.email)
            put("bearer", account.bearerToken)
        }.toString()
        runCatching { CloudflareCredentialStore.save(serialized) }
    }

    suspend fun signUp(email: String, password: String) {
        val name = email.substringBefore('@').take(48).ifBlank { "Kovelyx" }
        val body = buildJsonObject {
            put("name", name)
            put("email", email.trim())
            put("password", password)
        }.toString()
        request("/api/auth/sign-up/email", body)
        // La connexion est explicite; on ne présume pas de session à la création.
    }

    suspend fun signIn(email: String, password: String): Session {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }.toString()
        val (responseBody, responseHeaders) = request("/api/auth/sign-in/email", body)
        val result = json.parseToJsonElement(responseBody).jsonObject
        val user = result["user"]?.jsonObject ?: error("Kovelyx Cloud : utilisateur absent")
        val id = user["id"]?.jsonPrimitive?.content.orEmpty()
        val realEmail = user["email"]?.jsonPrimitive?.content.orEmpty()
        val headerToken = responseHeaders.entries.firstOrNull {
            it.key.equals("set-auth-token", ignoreCase = true)
        }?.value.orEmpty()
        val token = headerToken.ifBlank { result["token"]?.jsonPrimitive?.content.orEmpty() }
        check(id.isNotBlank() && realEmail.isNotBlank() && token.length >= 16) {
            "Kovelyx Cloud : session de connexion invalide"
        }
        return Session(id, realEmail, token).also {
            session = it
            persistSession(it)
        }
    }

    suspend fun signOut() {
        val old = session
        session = null
        deltaSnapshot = null
        batchSession = null
        CloudflareCredentialStore.clear()
        if (old != null) request("/api/auth/sign-out", "{}", old.bearerToken)
    }

    fun clearLocalSession() {
        session = null
        deltaSnapshot = null
        batchSession = null
        CloudflareCredentialStore.clear()
    }

    /** Révoquer uniquement l'ancienne session sans toucher à une nouvelle connexion. */
    suspend fun revokePreviousSession(bearerToken: String) {
        request("/api/auth/sign-out", "{}", bearerToken)
    }

    suspend fun accountProfiles(): JsonObject {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val response = httpRequestRaw(
            method = "GET", url = base() + "/v1/profiles",
            headers = mapOf("Authorization" to "Bearer ${active.bearerToken}"),
            body = "", followRedirects = false, maxResponseBodyBytes = 131072
        )
        check(response.status == 200) { "Kovelyx Cloud : profils indisponibles (HTTP ${response.status})" }
        return json.parseToJsonElement(response.body).jsonObject
    }

    /** Retrieve the stable, server-owned UUID for each D1 profile. */
    suspend fun createProfile(displayName: String): String {
        require(displayName.isNotBlank() && displayName.length <= 64)
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject { put("displayName", displayName.trim()) }.toString()
        val (response, _) = request("/v1/profiles", body, active.bearerToken)
        return json.parseToJsonElement(response).jsonObject["profile"]
            ?.jsonObject?.get("id")?.jsonPrimitive?.content
            ?: error("Kovelyx Cloud : identifiant de profil absent")
    }

    suspend fun renameProfile(profileId: String, displayName: String) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        require(displayName.isNotBlank() && displayName.length <= 64)
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject { put("displayName", displayName.trim()) }.toString()
        request("/v1/profiles?profileId=$profileId", body, active.bearerToken, "PATCH")
    }

    suspend fun deleteProfile(profileId: String) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        request("/v1/profiles?profileId=$profileId", "{}", active.bearerToken, "DELETE")
    }

    /** PINs are private D1 rows, never part of the downloadable kv_sync payload. */
    suspend fun cloudProfilePins(): JsonObject {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val (body, _) = request("/v1/profile-pins", "", active.bearerToken, "GET")
        check(session?.bearerToken == active.bearerToken) { "Compte modifié" }
        return json.parseToJsonElement(body).jsonObject
    }

    suspend fun verifyCloudProfilePin(profileId: String, pin: String): JsonObject {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject { put("profileId", profileId); put("pin", pin) }.toString()
        val (response, _) = request("/v1/profile-pin/verify", body, active.bearerToken)
        check(session?.bearerToken == active.bearerToken) { "Compte modifié" }
        return json.parseToJsonElement(response).jsonObject
    }

    suspend fun setCloudProfilePin(profileId: String, pin: String, currentPin: String? = null) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject {
            put("profileId", profileId)
            put("pin", pin)
            currentPin?.let { put("currentPin", it) }
        }.toString()
        request("/v1/profile-pin", body, active.bearerToken)
        check(session?.bearerToken == active.bearerToken) { "Compte modifié" }
    }

    suspend fun clearCloudProfilePin(profileId: String, currentPin: String?) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject {
            put("profileId", profileId)
            currentPin?.let { put("currentPin", it) }
        }.toString()
        request("/v1/profile-pin", body, active.bearerToken, "DELETE")
        check(session?.bearerToken == active.bearerToken) { "Compte modifié" }
    }

    /** Optimistic, revision-aware write; HTTP 409 is intentionally returned as an error. */
    suspend fun writeSync(
        profileId: String, category: String, itemKey: String,
        payload: JsonObject, revision: Int = 0, deleted: Boolean = false
    ): JsonObject {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val data = buildJsonObject {
            put("profileId", profileId)
            put("category", category)
            put("itemKey", itemKey)
            put("revision", revision)
            put("deleted", deleted)
            put("payload", payload)
        }
        val (response, _) = request("/v1/sync", data.toString(), active.bearerToken)
        check(session == active) { "Compte modifié" }
        return json.parseToJsonElement(response).jsonObject
    }

    suspend fun putCatalogueChunk(profileId: String, kind: String, hash: String, index: Int, text: String) {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject {
            put("profileId", profileId); put("category", "catalogues")
            put("itemKey", "snapshot:$kind:$hash:${index.toString().padStart(6, '0')}")
            put("revision", 0); put("deleted", false)
            put("payload", buildJsonObject { put("index", index); put("text", text) })
        }
        request("/v1/catalogue-chunk", body.toString(), active.bearerToken)
        check(session?.userId == active.userId) { "Compte modifié" }
    }

    suspend fun publishCatalogueHead(profileId: String, kind: String, head: com.kovelyx.cloud.catalogues.CatalogueHead) {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val body = buildJsonObject {
            put("profileId", profileId); put("kind", kind); put("sha256", head.sha256)
            put("parts", head.parts); put("utf8Bytes", head.utf8Bytes); put("revision", head.revision)
        }
        request("/v1/catalogue-manifest", body.toString(), active.bearerToken)
        check(session?.userId == active.userId) { "Compte modifié" }
    }

    /** Read all pages, instead of silently truncating the library at 500 entries. */
    // Keep one bounded, session-scoped snapshot. A delta is committed only
    // after every page succeeds. Old Workers transparently return offset pages.
    private data class DeltaSnapshot(
        val session: Session, val profileId: String, val cursor: Long,
        val rows: List<JsonObject>, val started: TimeMark
    )
    private val syncReadMutex = Mutex()
    private var deltaSnapshot: DeltaSnapshot? = null
    private var batchSession: Session? = null

    suspend fun synchronizeAll(profileId: String, snapshotKind: String? = null, snapshotHash: String? = null): List<JsonObject> = syncReadMutex.withLock {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        require((snapshotKind == null && snapshotHash == null) ||
            (snapshotKind in setOf("collections", "layout") && snapshotHash?.matches(Regex("[a-f0-9]{64}")) == true))
        val catalogueQuery = if (snapshotKind == null) "" else "&snapshotKind=$snapshotKind&snapshotHash=$snapshotHash"
        var delta = snapshotKind == null
        val cached = deltaSnapshot?.takeIf {
            delta && it.session == active && it.profileId == profileId && it.started.elapsedNow() < 6.hours
        }
        if (delta && cached == null) deltaSnapshot = null
        var cursor = cached?.cursor ?: 0L
        val changed = mutableListOf<JsonObject>()
        val all = mutableListOf<JsonObject>()
        var offset = 0
        var resetOnce = false
        while (true) {
            val query = if (delta) "&mode=delta&since=$cursor" else ""
            val response = httpRequestRaw(
                method = "GET", url = base() + "/v1/sync?profileId=$profileId&offset=$offset&limit=25$catalogueQuery$query",
                headers = mapOf("Authorization" to "Bearer ${active.bearerToken}"),
                body = "", followRedirects = false, maxResponseBodyBytes = 1048576
            )
            check(response.status == 200 && session == active) { "Kovelyx Cloud : synchronisation HTTP ${response.status} ou compte modifié" }
            val page = json.parseToJsonElement(response.body).jsonObject
            val rows = page["items"]?.jsonArray.orEmpty().map { it.jsonObject }
            if (delta && page["syncVersion"]?.jsonPrimitive?.intOrNull == 1) {
                if (page["reset"]?.jsonPrimitive?.booleanOrNull == true) {
                    check(!resetOnce) { "Curseur Cloudflare invalide" }
                    resetOnce = true
                    cursor = 0L
                    changed.clear()
                    deltaSnapshot = null
                    continue
                }
                val next = checkNotNull(page["nextSince"]?.jsonPrimitive?.longOrNull)
                var previous = cursor
                for (row in rows) {
                    val seq = checkNotNull(row["syncSeq"]?.jsonPrimitive?.longOrNull)
                    check(seq > previous) { "Delta Cloudflare désordonné" }
                    previous = seq
                }
                check(next == previous && rows.size <= 25 && changed.size + rows.size <= 100000) { "Pagination delta invalide" }
                changed.addAll(rows)
                cursor = next
                if (page["partial"]?.jsonPrimitive?.booleanOrNull == true) {
                    check(rows.isNotEmpty()) { "Delta Cloudflare incomplet" }
                    continue
                }
                check(session == active) { "Compte modifié" }
                val prior = if (resetOnce) null else cached
                val merged = if (changed.isEmpty() && prior != null) prior.rows else {
                    val byKey = linkedMapOf<Pair<String, String>, JsonObject>()
                    fun key(row: JsonObject) = row.getValue("category").jsonPrimitive.content to row.getValue("itemKey").jsonPrimitive.content
                    prior?.rows?.forEach { byKey[key(it)] = it }
                    changed.forEach { byKey[key(it)] = it }
                    byKey.values.toList()
                }
                // Limit retained memory; large accounts still use indexed delta pages
                // but restart from zero rather than retaining an unbounded snapshot.
                deltaSnapshot = if (merged.size <= 8000 &&
                    merged.sumOf { it.toString().length.toLong() } <= 4_000_000L)
                    DeltaSnapshot(active, profileId, cursor, merged, prior?.started ?: TimeSource.Monotonic.markNow()) else null
                batchSession = active
                return@withLock merged
            }
            if (delta) {
                check(page.containsKey("nextOffset")) { "Protocole Cloudflare inattendu" }
                delta = false
                deltaSnapshot = null
                batchSession = null
                changed.clear()
            }
            all.addAll(rows)
            val next = page["nextOffset"]?.jsonPrimitive?.intOrNull
            if (next == null) return@withLock all
            check(next == offset + 25 && rows.size == 25 && all.size <= 100000) { "Kovelyx Cloud : pagination invalide" }
            offset = next
        }
        @Suppress("UNREACHABLE_CODE")
        error("Pagination interrompue")
    }

    internal data class SyncWrite(val category: String, val key: String, val payload: JsonObject, val revision: Int)

    internal suspend fun writeSyncBatch(profileId: String, items: List<SyncWrite>) {
        if (items.isEmpty()) return
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        for (chunk in items.chunked(25)) {
            check(session == active) { "Compte modifié" }
            if (batchSession != active) {
                for (item in chunk) {
                    check(session == active) { "Compte modifié" }
                    writeSync(profileId, item.category, item.key, item.payload, item.revision)
                }
            } else {
                val body = buildJsonObject {
                    put("profileId", profileId)
                    put("items", JsonArray(chunk.map { item -> buildJsonObject {
                        put("category", item.category)
                        put("itemKey", item.key)
                        put("revision", item.revision)
                        put("deleted", false)
                        put("payload", item.payload)
                    } }))
                }
                val (reply, _) = request("/v1/sync-batch", body.toString(), active.bearerToken)
                check(session == active && json.parseToJsonElement(reply).jsonObject["written"]?.jsonPrimitive?.intOrNull == chunk.size) {
                    "Lot Cloudflare non confirmé"
                }
            }
        }
    }

    suspend fun synchronizeRaw(profileId: String): JsonObject {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}"))) { "Identifiant de profil invalide" }
        val response = httpRequestRaw(
            method = "GET", url = base() + "/v1/sync?profileId=$profileId",
            headers = mapOf("Authorization" to "Bearer ${active.bearerToken}"),
            body = "", followRedirects = false, maxResponseBodyBytes = 1048576
        )
        check(response.status == 200) { "Kovelyx Cloud : synchronisation indisponible (HTTP ${response.status})" }
        return json.parseToJsonElement(response.body).jsonObject
    }

    private fun mediaPath(profileId: String, kind: String): String {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")) && kind in setOf("avatar", "background"))
        return "/v1/profile-media?profileId=$profileId&kind=$kind"
    }

    suspend fun uploadProfileMedia(profileId: String, kind: String, expected: String?, image: com.kovelyx.cloud.media.ProfileImageBytes): String {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val response = httpRequestRaw("PUT", base() + mediaPath(profileId, kind), mapOf(
            "Authorization" to "Bearer ${active.bearerToken}", "Origin" to base(),
            "Content-Type" to image.mime, "If-Match" to (expected ?: "none"), "User-Agent" to "Kovelyx-Client"
        ), "", followRedirects = false, maxResponseBodyBytes = 4096, bodyBytes = image.bytes)
        check(response.status in 200..299) { "Le service d’images est indisponible ou doit être mis à jour (HTTP ${response.status})." }
        check(session?.userId == active.userId) { "Compte modifié" }
        return json.parseToJsonElement(response.body).jsonObject["version"]!!.jsonPrimitive.content
    }

    suspend fun downloadProfileMedia(profileId: String, kind: String, version: String): com.kovelyx.cloud.media.ProfileImageBytes {
        require(version.matches(Regex("[a-f0-9]{64}")))
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val response = httpRequestRaw("GET", base() + mediaPath(profileId, kind) + "&version=$version",
            mapOf("Authorization" to "Bearer ${active.bearerToken}", "User-Agent" to "Kovelyx-Client"), "",
            followRedirects = false, maxResponseBodyBytes = com.kovelyx.cloud.media.MAX_PROFILE_MEDIA_BYTES)
        check(response.status == 200 && session?.userId == active.userId) { "Téléchargement d’image non confirmé" }
        val mime = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.substringBefore(';').orEmpty()
        return com.kovelyx.cloud.media.ProfileImageBytes(mime, response.bodyBytes)
    }

    suspend fun removeProfileMedia(profileId: String, kind: String, expected: String?) {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val response = httpRequestRaw("DELETE", base() + mediaPath(profileId, kind), mapOf(
            "Authorization" to "Bearer ${active.bearerToken}", "Origin" to base(),
            "If-Match" to (expected ?: "none"), "User-Agent" to "Kovelyx-Client"
        ), "", followRedirects = false, maxResponseBodyBytes = 4096)
        check(response.status in 200..299 && session?.userId == active.userId) { "Suppression d’image non confirmée" }
    }
}
