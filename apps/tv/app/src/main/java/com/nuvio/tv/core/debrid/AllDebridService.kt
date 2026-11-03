package com.nuvio.tv.core.debrid

import com.kovelyx.debrid.*
import com.nuvio.tv.data.local.DebridSettingsDataStore
import com.nuvio.tv.domain.model.Stream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AllDebridService @Inject constructor(
    @Named("directDebrid") private val http: OkHttpClient,
    private val settings: DebridSettingsDataStore,
) {
    private val client = AllDebridClient(AllDebridTransport { endpoint, key, fields ->
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url("https://api.alldebrid.com/v4/$endpoint")
                .header("Authorization", "Bearer $key")
            if (fields.isNotEmpty()) request.post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
            http.newCall(request.build()).execute().use { response ->
                DebridHttpReply(response.code, response.body?.string().orEmpty())
            }
        }
    })
    suspend fun validate(key: String): Boolean = client.validate(key)
    suspend fun resolve(stream: Stream, season: Int?, episode: Int?): DirectDebridResolveResult {
        val resolve = stream.clientResolve ?: return DirectDebridResolveResult.Error
        val key = settings.settings.first().apiKeyFor(DebridProviders.ALL_DEBRID_ID)
        if (key.isBlank()) return DirectDebridResolveResult.MissingApiKey
        val magnet = resolve.magnetUri?.takeIf { it.isNotBlank() }
            ?: resolve.infoHash?.takeIf { it.isNotBlank() }?.let { "magnet:?xt=urn:btih:$it" }
            ?: return DirectDebridResolveResult.Stale
        return try {
            val files = client.files(key, magnet)
            if (files.isEmpty()) return DirectDebridResolveResult.NotCached
            val file = selectDebridFile(files, resolve, season, episode,
                path = { it.path }, isPlayable = { it.link.isNotBlank() && it.path.hasDebridVideoExtension() }, size = { it.size })
                ?: return DirectDebridResolveResult.Stale
            val link = client.unlock(key, file)
            DirectDebridResolveResult.Success(link.url, filename = link.filename ?: file.path.substringAfterLast('/'), videoSize = link.size ?: file.size)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { DirectDebridResolveResult.Error }
    }
}
