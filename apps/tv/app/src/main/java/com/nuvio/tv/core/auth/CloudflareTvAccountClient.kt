package com.nuvio.tv.core.auth

import com.nuvio.tv.BuildConfig
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client indépendant Workers / Better Auth : PAS de jeton Supabase.
 * La session native est conservée chiffrée par Android Keystore, puis
 * vérifiée avec /v1/me avant d'être rétablie après un redémarrage.
 */
internal object CloudflareTvAccountClient {
    data class Account(val id: String, val email: String, val bearerToken: String)
    val enabled: Boolean get() = BuildConfig.KOVELYX_CLOUD_URL.startsWith("https://")
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    @Volatile
    var account: Account? = null
        private set
    private var sessionStore: CloudflareTvSessionStore? = null

    /** Called from AuthManager at process startup; never assumes a valid remote session. */
    suspend fun restoreSession(context: Context): Account? = withContext(Dispatchers.IO) {
        sessionStore = CloudflareTvSessionStore(context)
        val saved = sessionStore?.load(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
            ?: return@withContext null
        if (!enabled) return@withContext null
        try {
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/me")
                .header("Authorization", "Bearer ${saved.bearerToken}")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    sessionStore?.clear()
                    return@withContext null
                }
                if (!response.isSuccessful) {
                    Log.w("KovelyxTvAuth", "Vérification de session temporairement indisponible : HTTP ${response.code}")
                    return@withContext null
                }
                val me = JSONObject(response.body?.string().orEmpty())
                if (me.optString("id") != saved.id) {
                    sessionStore?.clear()
                    return@withContext null
                }
                if (account == null) account = saved
                account
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Network failure: keep encrypted credentials for the next launch.
            Log.w("KovelyxTvAuth", "Impossible de vérifier la session sauvegardée", error)
            null
        }
    }

    private suspend fun post(endpoint: String, email: String, password: String): Pair<JSONObject,String> =
        withContext(Dispatchers.IO) {
            check(enabled) { "Kovelyx Cloudflare non configuré" }
            val json = JSONObject().put("email", email.trim()).put("password", password)
            if (endpoint == "sign-up") json.put("name", email.substringBefore('@').take(48).ifBlank { "Kovelyx" })
            val route = if (endpoint == "sign-up") "sign-up/email" else "sign-in/email"
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/api/auth/" + route)
                .header("Accept", "application/json")
                // Better Auth requires a trusted Origin for mutations. The native
                // app uses the same HTTPS Worker host rather than a browser origin.
                .header("Origin", BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                check(response.isSuccessful) { "Kovelyx Cloud : HTTP ${response.code}" }
                JSONObject(body) to response.header("set-auth-token").orEmpty()
            }
        }

    suspend fun signUp(email: String, password: String) {
        post("sign-up", email, password)
    }

    suspend fun signIn(email: String, password: String): Account {
        val (body, headerToken) = post("sign-in", email, password)
        val user = body.optJSONObject("user") ?: error("Kovelyx Cloud : utilisateur absent")
        val id = user.optString("id")
        val address = user.optString("email")
        val token = headerToken.ifBlank { body.optString("token") }
        check(id.isNotBlank() && address.isNotBlank() && token.length >= 16) {
            "Kovelyx Cloud : jeton invalide"
        }
        return Account(id, address, token).also { signedIn ->
            account = signedIn
            runCatching {
                sessionStore?.save(signedIn, BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
            }.onFailure { error ->
                Log.w("KovelyxTvAuth", "Session connectée mais stockage chiffré indisponible", error)
            }
        }
    }

    /** Always clear the device first; network revocation must never block local logout. */
    suspend fun signOut() {
        val token = account?.bearerToken
        clearLocalSession()
        if (!token.isNullOrBlank()) revokePreviousSession(token)
    }

    /** Revokes only the captured old token, never a later account's new session. */
    suspend fun revokePreviousSession(previousBearerToken: String) {
        if (!enabled || previousBearerToken.isBlank()) return
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/api/auth/sign-out")
                .header("Authorization", "Bearer $previousBearerToken")
                .header("Origin", BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("KovelyxTvAuth", "Ancienne session non révoquée HTTP ${response.code}; déconnexion locale déjà effective")
                }
            }
        }
    }

    suspend fun getCloudProfileNames(): List<String> = withContext(Dispatchers.IO) {
        val loggedIn = checkNotNull(account) { "Compte Kovelyx Cloud non connecté" }
        check(enabled) { "Kovelyx Cloudflare non configuré" }
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/profiles")
            .header("Authorization", "Bearer ${loggedIn.bearerToken}")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Kovelyx Cloud : profils HTTP ${response.code}" }
            val json = JSONObject(response.body?.string().orEmpty())
            val profiles = json.optJSONArray("profiles") ?: return@use emptyList()
            (0 until profiles.length()).mapNotNull { index ->
                profiles.optJSONObject(index)?.optString("displayName")?.takeIf { it.isNotBlank() }
            }
        }
    }

    /**
     * Creates the first cloud profile only for a genuinely empty account.
     * TV keeps the returned D1 UUID; numeric local profile IDs are not cloud IDs.
     */
    suspend fun createCloudProfile(displayName: String): String = withContext(Dispatchers.IO) {
        val active = checkNotNull(account) { "Compte Kovelyx non connecté" }
        check(enabled) { "Kovelyx Cloudflare non configuré" }
        require(displayName.isNotBlank() && displayName.length <= 64)
        val root = BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/')
        val data = JSONObject().put("displayName", displayName.trim())
        val request = Request.Builder()
            .url("$root/v1/profiles")
            .header("Authorization", "Bearer ${active.bearerToken}")
            .header("Origin", root)
            .post(data.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            check(response.code == 201) { "Création du profil Cloudflare HTTP ${response.code}" }
            val id = JSONObject(response.body?.string().orEmpty())
                .optJSONObject("profile")?.optString("id").orEmpty()
            check(id.matches(Regex("[a-fA-F0-9-]{36}"))) {
                "Le serveur a renvoyé un profil Cloudflare invalide."
            }
            id
        }
    }

    /** Stable D1 profile IDs must be used instead of the TV's local numeric index. */
    suspend fun cloudProfiles(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val active = checkNotNull(account) { "Compte Kovelyx non connecté" }
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/profiles")
            .header("Authorization", "Bearer ${active.bearerToken}")
            .get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Cloudflare profils HTTP ${response.code}" }
            val entries = JSONObject(response.body?.string().orEmpty())
                .optJSONArray("profiles") ?: return@use emptyList()
            (0 until entries.length()).mapNotNull { i ->
                entries.optJSONObject(i)?.let { data ->
                    val id = data.optString("id")
                    if (id.length == 36) id to data.optString("displayName") else null
                }
            }
        }
    }

    suspend fun syncRows(profileId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val active = checkNotNull(account) { "Compte Kovelyx non connecté" }
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val rows = mutableListOf<JSONObject>()
        var offset = 0
        do {
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') +
                    "/v1/sync?profileId=$profileId&offset=$offset&limit=25")
                .header("Authorization", "Bearer ${active.bearerToken}")
                .get().build()
            val next = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Cloudflare sync HTTP ${response.code}" }
                val page = JSONObject(response.body?.string().orEmpty())
                val values = page.optJSONArray("items") ?: org.json.JSONArray()
                for (i in 0 until values.length()) {
                    values.optJSONObject(i)?.let(rows::add)
                }
                if (page.isNull("nextOffset")) -1 else page.optInt("nextOffset", -1)
            }
            if (next == -1) break
            check(next == offset + 25 && rows.size <= 100000) {
                "Pagination Cloudflare invalide"
            }
            offset = next
        } while (true)
        rows
    }

    suspend fun writeSync(
        profileId: String,
        category: String,
        itemKey: String,
        payload: JSONObject,
        revision: Int = 0,
        deleted: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val active = checkNotNull(account) { "Compte Kovelyx non connecté" }
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val body = JSONObject()
            .put("profileId", profileId).put("category", category)
            .put("itemKey", itemKey).put("payload", payload)
            .put("revision", revision).put("deleted", deleted)
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/sync")
            .header("Authorization", "Bearer ${active.bearerToken}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Cloudflare sync HTTP ${response.code}" }
        }
    }

    fun clearLocalSession() {
        account = null
        sessionStore?.clear()
    }
}
