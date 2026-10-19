package com.nuvio.tv.core.auth

import com.nuvio.tv.BuildConfig
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
 * Préversion : les sessions sont en mémoire et ne survivent pas au redémarrage,
 * jusqu'à la mise en place d'un coffre chiffré Android Keystore.
 */
internal object CloudflareTvAccountClient {
    data class Account(val id: String, val email: String, val bearerToken: String)
    val enabled: Boolean get() = BuildConfig.KOVELYX_CLOUD_URL.startsWith("https://")
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
    var account: Account? = null
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
        return Account(id, address, token).also { account = it }
    }

    suspend fun signOut() {
        val old = account
        account = null
        if (old == null || !enabled) return
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BuildConfig.KOVELYX_CLOUD_URL.trimEnd('/') + "/api/auth/sign-out")
                .header("Authorization", "Bearer ${old.bearerToken}")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
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
        if(cached.isFile && cached.length() in 1..(20L*1024*1024))
            return@withContext cached.toURI().toString()
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
                check(tmp.renameTo(cached)) { "Impossible de sauvegarder le média TV" }
                cached.toURI().toString()
            } finally { tmp.delete() }
        }
    }

    fun clearLocalSession() {
        account = null
    }
}
