package com.nuvio.app.core.cloud

import com.nuvio.app.features.addons.httpRequestRaw
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
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
 * Sessions en mémoire pour cette phase de migration : ne pas persister de jetons
 * dans les préférences non chiffrées. L'utilisateur se reconnecte au redémarrage
 * jusqu'à l'intégration d'un stockage sécurisé spécifique aux OS.
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

    private suspend fun request(path: String, body: String, authorization: String? = null): Pair<String, Map<String,String>> {
        val headers = mutableMapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "User-Agent" to "Kovelyx-Client"
        )
        if (!authorization.isNullOrBlank()) headers["Authorization"] = "Bearer $authorization"
        val response = httpRequestRaw(
            method = "POST", url = base() + path, headers = headers, body = body,
            followRedirects = false, maxResponseBodyBytes = 131072
        )
        if (response.status !in 200..299) {
            val description = runCatching {
                json.parseToJsonElement(response.body).jsonObject.let { body ->
                    body["message"]?.jsonPrimitive?.content
                        ?: body["error"]?.jsonPrimitive?.content
                }
            }.getOrNull().orEmpty()
            error("Kovelyx Cloud : HTTP ${response.status}" + if (description.isNotBlank()) " — $description" else "")
        }
        return response.body to response.headers
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
        return Session(id, realEmail, token).also { session = it }
    }

    suspend fun signOut() {
        val old = session
        session = null
        if (old != null) request("/api/auth/sign-out", "{}", old.bearerToken)
    }

    fun clearLocalSession() {
        session = null
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


    private suspend fun profileWrite(path: String, body: String): JsonObject {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        val (response, _) = request(path, body, active.bearerToken)
        return json.parseToJsonElement(response).jsonObject
    }

    suspend fun createAccountProfile(name: String): JsonObject {
        require(name.isNotBlank() && name.trim().length <= 64) { "Nom de profil invalide" }
        val body = buildJsonObject { put("displayName", name.trim()) }
        return checkNotNull(profileWrite("/v1/profiles", body.toString())["profile"]).jsonObject
    }

    suspend fun renameAccountProfile(profileId: String, name: String) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}"))) { "Profil invalide" }
        require(name.isNotBlank() && name.trim().length <= 64) { "Nom de profil invalide" }
        val body = buildJsonObject {
            put("profileId", profileId)
            put("displayName", name.trim())
        }
        checkNotNull(profileWrite("/v1/profiles/update", body.toString())["profile"])
    }

    suspend fun deleteAccountProfile(profileId: String) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}"))) { "Profil invalide" }
        val body = buildJsonObject { put("profileId", profileId) }
        check(profileWrite("/v1/profiles/delete", body.toString())["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
            "Suppression du profil non confirmée"
        }
    }


    /**
     * Parcourt entièrement une catégorie D1. Chaque page est bornée (100 lignes)
     * et l'index évolue par item_key, sans confondre révision individuelle et curseur global.
     * En cas d'erreur réseau/pagination, on ne renvoie jamais un jeu partiel.
     */
    suspend fun syncCategory(profileId: String, category: String): List<JsonObject> {
        val active = checkNotNull(session) { "Compte Kovelyx non connecté" }
        require(profileId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Profil cloud invalide" }
        require(category in setOf("library", "favorites", "watch_progress",
            "catalogues", "preferences", "profile_settings", "addons", "plugins"))
        val items = mutableListOf<JsonObject>()
        var after: String? = null
        repeat(100) {
            val suffix = after?.let { "&after=${it.encodeURLParameter()}" }.orEmpty()
            val response = httpRequestRaw(
                method = "GET",
                url = base() + "/v1/sync?profileId=$profileId&category=$category&limit=100$suffix",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${active.bearerToken}"
                ),
                body = "", followRedirects = false, maxResponseBodyBytes = 1048576
            )
            check(response.status == 200) { "Kovelyx Cloud : lecture $category HTTP ${response.status}" }
            val page = json.parseToJsonElement(response.body).jsonObject
            val rows = page["items"]?.jsonArray ?: error("Réponse de synchronisation invalide")
            items += rows.map { it.jsonObject }
            val next = page["nextCursor"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            if (next == null) {
                check(page["partial"]?.jsonPrimitive?.booleanOrNull != true) {
                    "Réponse de synchronisation incomplète"
                }
                return items
            }
            check(next != after && next.length <= 200) { "Pagination cloud incohérente" }
            after = next
        }
        error("Trop d'éléments pour la synchronisation : données locales préservées")
    }

    suspend fun writeSync(
        profileId: String,
        category: String,
        itemKey: String,
        payload: JsonObject,
        revision: Int,
        deleted: Boolean
    ): Int {
        require(profileId.matches(Regex("[0-9a-fA-F-]{36}"))) { "Profil cloud invalide" }
        require(category in setOf("library", "favorites", "watch_progress",
            "catalogues", "preferences", "profile_settings", "addons", "plugins"))
        require(itemKey.isNotBlank() && itemKey.length <= 200)
        require(revision >= 0)
        val body = buildJsonObject {
            put("profileId", profileId)
            put("category", category)
            put("itemKey", itemKey)
            put("payload", payload)
            put("revision", revision)
            put("deleted", deleted)
        }
        val (result, _) = request("/v1/sync", body.toString(),
            checkNotNull(session).bearerToken)
        return json.parseToJsonElement(result).jsonObject["revision"]
            ?.jsonPrimitive?.intOrNull ?: error("Révision D1 non confirmée")
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
