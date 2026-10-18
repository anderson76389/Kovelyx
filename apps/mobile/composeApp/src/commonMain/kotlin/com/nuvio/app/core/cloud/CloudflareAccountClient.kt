package com.nuvio.app.core.cloud

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
                json.parseToJsonElement(response.body).jsonObject["message"]?.jsonPrimitive?.content
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
