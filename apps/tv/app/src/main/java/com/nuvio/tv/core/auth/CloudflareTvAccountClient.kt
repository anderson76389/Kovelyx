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
                // Client Android natif : pas d'en-tête Origin de navigateur.
                // Un Origin auto-ajouté rejetait la connexion (HTTP 403) côté Worker.
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

    suspend fun signOut() {
        val old = account
        account = null
        sessionStore?.clear()
        if (old == null || !enabled) return
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/api/auth/sign-out")
                .header("Authorization", "Bearer ${old.bearerToken}")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("KovelyxTvAuth", "Révocation distante HTTP ${response.code} : session locale déjà effacée")
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

    fun clearLocalSession() {
        account = null
        sessionStore?.clear()
    }
}
