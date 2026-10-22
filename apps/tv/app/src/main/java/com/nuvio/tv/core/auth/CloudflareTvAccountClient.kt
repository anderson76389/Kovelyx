package com.nuvio.tv.core.auth

import com.nuvio.tv.BuildConfig
import android.util.Log
import java.io.IOException
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
 * La session est conservée localement sous AES-GCM AndroidKeyStore et validée
 * auprès de Cloudflare avant toute restauration d'identité.
 */
internal object CloudflareTvAccountClient {
    data class Account(val id: String, val email: String, val bearerToken: String)
    val enabled: Boolean get() = BuildConfig.KOVELYX_CLOUD_URL.startsWith("https://")
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    private val sessionLock = Any()
    @Volatile var account: Account? = null
        private set

    private suspend fun post(endpoint: String, email: String, password: String): Pair<JSONObject,String> =
        withContext(Dispatchers.IO) {
            check(enabled) { "Kovelyx Cloudflare non configuré" }
            val json = JSONObject().put("email", email.trim()).put("password", password)
            if (endpoint == "sign-up") json.put("name", email.substringBefore('@').take(48).ifBlank { "Kovelyx" })
            val route = if (endpoint == "sign-up") "sign-up/email" else "sign-in/email"
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/api/auth/" + route)
                .header("Accept", "application/json")
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

    suspend fun signIn(context: android.content.Context, email: String, password: String): Account {
        val (body, headerToken) = post("sign-in", email, password)
        val user = body.optJSONObject("user") ?: error("Kovelyx Cloud : utilisateur absent")
        val id = user.optString("id")
        val address = user.optString("email")
        val token = headerToken.ifBlank { body.optString("token") }
        check(id.isNotBlank() && address.isNotBlank() && token.length >= 16) {
            "Kovelyx Cloud : jeton invalide"
        }
        return Account(id, address, token).also {
            synchronized(sessionLock) {
                TvCloudSessionVault.save(context, it)
                account = it
            }
        }
    }

    suspend fun signOut(context: android.content.Context) {
        val old = synchronized(sessionLock) {
            val previous = account
            // Ne jamais afficher une déconnexion réussie si l'effacement du
            // coffre a échoué : sinon l'ancien compte réapparaît au redémarrage.
            TvCloudSessionVault.clear(context)
            account = null
            previous
        }
        if (old == null || !enabled) return
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/api/auth/sign-out")
                .header("Authorization", "Bearer ${old.bearerToken}")
                .header("Origin", BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("KovelyxTvAuth", "Révocation distante refusée HTTP ${response.code} ; session locale effacée")
                    }
                }
            } catch (error: IOException) {
                // Hors ligne : ne jamais garder une session affichée comme connectée
                // en attendant la disponibilité du serveur.
                Log.w("KovelyxTvAuth", "Révocation distante différée (session locale effacée)", error)
            }
        }
    }

    data class CloudProfile(val id:String,val name:String,
                            val avatarPath:String?,val backgroundPath:String?)

    suspend fun getCloudProfiles():List<CloudProfile> = withContext(Dispatchers.IO) {
        val loggedIn = checkNotNull(account) { "Compte Kovelyx Cloud non connecté" }
        check(enabled) { "Kovelyx Cloudflare non configuré" }
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/profiles")
            .header("Authorization", "Bearer ${loggedIn.bearerToken}")
            .header("Accept", "application/json").get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Kovelyx Cloud : profils HTTP ${response.code}" }
            val json=JSONObject(response.body?.string().orEmpty())
            check(account == loggedIn) { "Compte changé pendant la lecture des profils" }
            val profiles=json.optJSONArray("profiles") ?: return@use emptyList()
            (0 until profiles.length()).mapNotNull { index ->
                val row=profiles.optJSONObject(index) ?: return@mapNotNull null
                val id=row.optString("id")
                val name=row.optString("displayName")
                if(!id.matches(Regex("[0-9a-fA-F-]{36}"))||name.isBlank()) null
                else CloudProfile(id,name,
                    row.optString("avatarPath").takeIf { it.isNotBlank() && it!="null" },
                    row.optString("backgroundPath").takeIf { it.isNotBlank() && it!="null" })
            }
        }
    }

    suspend fun getCloudProfileNames():List<String> = getCloudProfiles().map { it.name }

    /** Téléchargement privé : ne jamais exposer le jeton dans une URL Coil. */
    suspend fun downloadMedia(
        context:android.content.Context,
        profile:CloudProfile,
        background:Boolean
    ):String? = withContext(Dispatchers.IO) {
        val path=if(background) profile.backgroundPath else profile.avatarPath
        if(path.isNullOrBlank()) return@withContext null
        val loggedIn=checkNotNull(account)
        val ext=path.substringAfterLast('.', "").lowercase()
        val mime=mapOf("jpg" to "image/jpeg","png" to "image/png",
            "gif" to "image/gif","webp" to "image/webp",
            "mp4" to "video/mp4","webm" to "video/webm",
            "mov" to "video/quicktime")
        if(ext !in mime) return@withContext null
        val cacheDir=java.io.File(context.filesDir,"kovelyx/profile-media").apply { mkdirs() }
        val hash=java.security.MessageDigest.getInstance("SHA-256")
            .digest((loggedIn.id+"|"+profile.id+"|"+path).toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }
        val cached=java.io.File(cacheDir,(if(background)"background-" else "avatar-")+"cloud-$hash.$ext")
        if(cached.isFile && cached.length() in 1..(20L*1024*1024)) {
            check(account == loggedIn) { "Compte changé pendant le chargement du média" }
            return@withContext cached.toURI().toString()
        }
        val kind=if(background)"background" else "avatar"
        val req=Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/')+
                "/v1/media?profileId=${profile.id}&kind=$kind")
            .header("Authorization","Bearer ${loggedIn.bearerToken}")
            .get().build()
        client.newCall(req).execute().use { response ->
            if(response.code==404) return@use null
            check(response.isSuccessful) { "R2 indisponible (HTTP ${response.code})" }
            check(response.header("Content-Type")?.substringBefore(';')==mime.getValue(ext)) {
                "Format de média distant inattendu"
            }
            val tmp=java.io.File.createTempFile("cloud-media-", ".part",cacheDir)
            try {
                response.body?.byteStream()?.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer=ByteArray(16384)
                        var size=0L
                        while(true) {
                            val n=input.read(buffer)
                            if(n<0) break
                            size+=n
                            check(size<=20L*1024*1024) { "Média cloud trop gros" }
                            output.write(buffer,0,n)
                        }
                    }
                } ?: error("Média cloud vide")
                check(tmp.length() in 1..(20L*1024*1024))
                // La session peut changer pendant le téléchargement R2.
                // Ne pas exposer l'avatar du compte A au compte B.
                check(account == loggedIn) { "Compte changé pendant le téléchargement du média" }
                check(tmp.renameTo(cached)) { "Impossible de sauvegarder le média TV" }
                if (account != loggedIn) {
                    cached.delete()
                    error("Compte changé avant l'affichage du média")
                }
                cached.toURI().toString()
            } finally { tmp.delete() }
        }
    }

    /** Ne connecte pas l'utilisateur avant confirmation par GET /v1/me. */
    suspend fun restoreVerified(context: android.content.Context): Account? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        account?.let { return@withContext it }
        val saved = TvCloudSessionVault.read(context) ?: return@withContext null
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/me")
            .header("Authorization", "Bearer ${saved.bearerToken}")
            .header("Accept", "application/json")
            .get().build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val user = JSONObject(response.body?.string().orEmpty())
                        synchronized(sessionLock) {
                            if (TvCloudSessionVault.read(context) != saved) {
                                // L'utilisateur a pu se déconnecter pendant la requête.
                                account
                            } else if (user.optString("id") != saved.id ||
                                !user.optString("email").equals(saved.email, ignoreCase = true)) {
                                TvCloudSessionVault.clear(context)
                                null
                            } else {
                                account ?: saved.also { account = it }
                            }
                        }
                    }
                    401, 403 -> synchronized(sessionLock) {
                        if (TvCloudSessionVault.read(context) == saved)
                            TvCloudSessionVault.clear(context)
                        account
                    }
                    else -> null
                }
            }
        } catch (error: Exception) {
            // L'annulation (déconnexion/navigation) ne doit jamais être cachée
            // comme une panne réseau : elle doit interrompre la restauration.
            if (error is kotlinx.coroutines.CancellationException) throw error
            // Une panne réseau conserve le coffre, sans autoriser la session.
            null
        }
    }

    fun clearLocalSession(context: android.content.Context) {
        synchronized(sessionLock) {
            // Le jeton chiffré doit disparaître avant de publier l'état invité.
            TvCloudSessionVault.clear(context)
            account = null
        }
    }

    /**
     * Lecture paginée D1 strictement limitée à un compte et un profil vérifiés.
     * Le même contrat watch_progress est utilisé par Windows et Android.
     * Aucune donnée de lecture locale TV n'est modifiée par cet appel.
     */
    private suspend fun readSyncCategory(profileId: String, category: String): List<JSONObject> =
        withContext(Dispatchers.IO) {
            require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
            require(category == "library" || category == "watch_progress")
            val owner = checkNotNull(account) { "Compte cloud non connecté" }
            val rows = mutableListOf<JSONObject>()
            var cursor: String? = null
            repeat(100) {
                check(account == owner) { "Compte changé pendant la lecture D1" }
                val query = cursor?.let {
                    "&after=" + java.net.URLEncoder.encode(it, "UTF-8")
                }.orEmpty()
                val request = Request.Builder()
                    .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') +
                        "/v1/sync?profileId=$profileId&category=$category&limit=100$query")
                    .header("Authorization", "Bearer ${owner.bearerToken}")
                    .header("Accept", "application/json")
                    .get().build()
                val page = client.newCall(request).execute().use { response ->
                    check(response.code == 200) { "Lecture D1 $category HTTP ${response.code}" }
                    JSONObject(response.body?.string().orEmpty())
                }
                check(account == owner) { "Compte changé pendant la lecture D1" }
                val items = page.getJSONArray("items")
                require(items.length() <= 100)
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    check(item.optString("category") == category) {
                        "Catégorie inattendue dans la réponse D1"
                    }
                    rows.add(item)
                }
                require(rows.size <= 10000)
                val next = page.optString("nextCursor").takeUnless { it.isBlank() || it == "null" }
                if (next == null) {
                    check(!page.optBoolean("partial", false)) { "Réponse D1 tronquée" }
                    return@withContext rows
                }
                check(next != cursor && next.length <= 200) { "Pagination D1 incohérente" }
                cursor = next
            }
            error("Limite D1 de 10 000 éléments atteinte : réponse partielle refusée")
        }

    suspend fun readLibrary(profileId: String): List<JSONObject> =
        readSyncCategory(profileId, "library")

    /** Progression Windows/Android visible en lecture seule tant que le lecteur TV
     * n'est pas relié à un cache D1 isolé et testé. Jamais de suppression implicite. */
    suspend fun readProgress(profileId: String): List<JSONObject> =
        readSyncCategory(profileId, "watch_progress")

    /** Historique des oeuvres vues, séparé des positions et de la bibliothèque. */
    suspend fun readWatched(profileId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        val owner = checkNotNull(account) { "Compte TV non connecté" }
        val rows = mutableListOf<JSONObject>()
        var cursor: String? = null
        repeat(100) {
            check(account == owner) { "Compte changé pendant l'historique" }
            val suffix = cursor?.let {
                "&after=" + java.net.URLEncoder.encode(it, "UTF-8")
            }.orEmpty()
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') +
                    "/v1/watched?profileId=$profileId&limit=100$suffix")
                .header("Authorization", "Bearer ${owner.bearerToken}")
                .header("Accept", "application/json")
                .get().build()
            val response = client.newCall(request).execute().use { result ->
                check(result.isSuccessful) { "Historique Cloud TV HTTP ${result.code}" }
                JSONObject(result.body?.string().orEmpty())
            }
            check(account == owner) { "Compte changé pendant l'historique" }
            val page = response.getJSONArray("items")
            check(page.length() <= 100)
            for (i in 0 until page.length()) rows.add(page.getJSONObject(i))
            check(rows.size <= 10000) { "Historique trop volumineux" }
            val next = response.optString("nextCursor")
                .takeUnless { it.isBlank() || it == "null" }
            if (next == null) {
                check(!response.optBoolean("partial", false)) { "Historique D1 partiel" }
                return@withContext rows
            }
            check(next != cursor && next.length <= 200) { "Pagination D1 incohérente" }
            cursor = next
        }
        error("Historique cloud tronqué : aucun résultat partiel appliqué")
    }

    /** Révision CAS : une suppression hors connexion ne peut écraser le voisin. */
    suspend fun writeWatched(
        profileId: String, itemKey: String, payload: JSONObject,
        revision: Int, deleted: Boolean
    ): Int = withContext(Dispatchers.IO) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        require(itemKey.length in 1..200 && revision >= 0)
        val owner = checkNotNull(account) { "Compte TV non connecté" }
        val body = JSONObject()
            .put("profileId", profileId)
            .put("itemKey", itemKey)
            .put("payload", payload)
            .put("revision", revision)
            .put("deleted", deleted)
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/watched")
            .header("Authorization", "Bearer ${owner.bearerToken}")
            .header("Origin", BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val result = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "Historique D1 HTTP ${response.code} (modifications locales préservées)"
            }
            JSONObject(response.body?.string().orEmpty())
        }
        check(account == owner) { "Compte changé pendant l'écriture" }
        result.getInt("revision").also { check(it > revision) }
    }

    /** Ecriture optimiste avec contrôle de révision (409 = conserver la mutation locale). */
    suspend fun writeLibrary(
        profileId: String, itemKey: String, item: JSONObject?,
        revision: Int, deleted: Boolean
    ): Int = withContext(Dispatchers.IO) {
        require(profileId.matches(Regex("[a-fA-F0-9-]{36}")))
        require(itemKey.length in 1..200 && revision >= 0)
        val token = checkNotNull(account).bearerToken
        val payload = JSONObject()
        if (!deleted) payload.put("item", checkNotNull(item))
        val body = JSONObject()
            .put("profileId", profileId).put("category", "library")
            .put("itemKey", itemKey).put("revision", revision)
            .put("deleted", deleted).put("payload", payload)
        val request = Request.Builder()
            .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/v1/sync")
            .header("Authorization", "Bearer $token")
            .header("Origin", BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/'))
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute().use { result ->
            check(result.isSuccessful) { "Écriture D1 HTTP ${result.code} (conflit conservé)" }
            JSONObject(result.body?.string().orEmpty())
        }
        check(account?.bearerToken == token) { "Compte changé pendant l'écriture" }
        response.getInt("revision").also { check(it > revision) }
    }
}
