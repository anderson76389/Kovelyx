package com.nuvio.app.core.cloud

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.core.auth.CloudflareCredentialStore
import kotlinx.serialization.json.Json
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
 * dans le client Supabase historique : les protocoles sont incompatibles.
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
        CloudflareCredentialStore.clear()
        if (old != null) request("/api/auth/sign-out", "{}", old.bearerToken)
    }

    fun clearLocalSession() {
        session = null
        CloudflareCredentialStore.clear()
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
        return json.parseToJsonElement(response).jsonObject
    }

    /** Read all pages, instead of silently truncating the library at 500 entries. */
    suspend fun synchronizeAll(profileId: String): List<JsonObject> {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val all = mutableListOf<JsonObject>()
        var offset = 0
        while (true) {
            val response = httpRequestRaw(
                method = "GET", url = base() + "/v1/sync?profileId=$profileId&offset=$offset&limit=25",
                headers = mapOf("Authorization" to "Bearer ${active.bearerToken}"),
                body = "", followRedirects = false, maxResponseBodyBytes = 1048576
            )
            check(response.status == 200) { "Kovelyx Cloud : synchronisation HTTP ${response.status}" }
            val page = json.parseToJsonElement(response.body).jsonObject
            val rows = page["items"]?.jsonArray.orEmpty().map { it.jsonObject }
            all.addAll(rows)
            val next = page["nextOffset"]?.jsonPrimitive?.intOrNull
            if (next == null) return all
            check(next > offset && rows.size == 25 && all.size <= 100000) {
                "Kovelyx Cloud : pagination invalide"
            }
            offset = next
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
}
