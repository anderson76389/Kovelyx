package com.nuvio.app.features.debrid

import com.kovelyx.debrid.*
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException

internal class AllDebridProviderApi(private val baseUrl: String = "https://api.alldebrid.com/v4/") : DebridProviderApi {
    override val provider = DebridProviders.AllDebrid
    private val client = AllDebridClient(AllDebridTransport { endpoint, key, fields ->
        val response = httpRequestRaw(
            method = if (fields.isEmpty()) "GET" else "POST",
            url = "$baseUrl$endpoint",
            headers = mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/x-www-form-urlencoded"),
            body = fields.joinToString("&") { (name, value) -> "${encodeFormValue(name)}=${encodeFormValue(value)}" },
        )
        DebridHttpReply(response.status, response.body)
    })
    override suspend fun validateApiKey(apiKey: String) = client.validate(apiKey)
    override suspend fun resolveClientStream(stream: StreamItem, apiKey: String, season: Int?, episode: Int?): DirectDebridResolveResult {
        val resolve = stream.clientResolve ?: return DirectDebridResolveResult.Error
        val magnet = resolve.magnetUri?.takeIf { it.isNotBlank() } ?: buildMagnetUri(resolve)
            ?: return DirectDebridResolveResult.Stale
        return try {
            val files = client.files(apiKey, magnet)
            if (files.isEmpty()) return DirectDebridResolveResult.NotCached
            val file = selectDebridFile(files, resolve, season, episode,
                path = { it.path }, isPlayable = { it.link.isNotBlank() && it.path.hasVideoExtension() }, size = { it.size })
                ?: return DirectDebridResolveResult.Stale
            val link = client.unlock(apiKey, file)
            DirectDebridResolveResult.Success(link.url, filename = link.filename ?: file.path.substringAfterLast('/'), videoSize = link.size ?: file.size)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { DirectDebridResolveResult.Error }
    }
}
