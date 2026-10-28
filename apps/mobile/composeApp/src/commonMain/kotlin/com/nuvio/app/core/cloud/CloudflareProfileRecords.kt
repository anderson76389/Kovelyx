package com.nuvio.app.core.cloud

import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Read/write non-secret profile documents with authenticated, revisioned D1 rows. */
object CloudflareProfileRecords {
    private val uuidPattern = Regex("[a-fA-F0-9-]{36}")

    suspend fun profileUuid(localId: Int): String {
        val account = AuthRepository.state.value as? AuthState.Authenticated
            ?: error("Compte Cloudflare non connecté")
        check(!account.isAnonymous && CloudflareAccountClient.currentSession?.userId == account.userId) {
            "Session Cloudflare invalide"
        }
        var id = ProfileRepository.state.value.profiles
            .firstOrNull { it.profileIndex == localId }?.id.orEmpty()
        if (!uuidPattern.matches(id)) {
            ProfileRepository.pullProfiles()
            id = ProfileRepository.state.value.profiles
                .firstOrNull { it.profileIndex == localId }?.id.orEmpty()
        }
        check(uuidPattern.matches(id)) { "Profil Cloudflare sans identifiant distant" }
        return id
    }

    suspend fun records(localId: Int): List<JsonObject> =
        CloudflareAccountClient.synchronizeAll(profileUuid(localId))

    fun lookup(rows: List<JsonObject>, category: String, key: String): JsonObject? =
        rows.firstOrNull {
            it["category"]?.jsonPrimitive?.content == category &&
                it["itemKey"]?.jsonPrimitive?.content == key
        }

    suspend fun write(localId: Int, category: String, key: String, payload: JsonObject) {
        val id = profileUuid(localId)
        repeat(3) { retry ->
            val current = lookup(CloudflareAccountClient.synchronizeAll(id), category, key)
            val revision = current?.get("revision")?.jsonPrimitive?.intOrNull ?: 0
            try {
                CloudflareAccountClient.writeSync(
                    profileId = id, category = category, itemKey = key,
                    payload = payload, revision = revision,
                )
                return
            } catch (error: Exception) {
                if (retry == 2 || !error.message.orEmpty().contains("409")) throw error
            }
        }
    }
}
