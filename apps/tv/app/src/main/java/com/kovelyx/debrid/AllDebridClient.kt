package com.kovelyx.debrid

import kotlinx.serialization.json.*

data class DebridHttpReply(val status: Int, val body: String)
fun interface AllDebridTransport {
    suspend fun request(endpoint: String, apiKey: String, fields: List<Pair<String, String>>): DebridHttpReply
}
data class AllDebridFile(val path: String, val size: Long, val link: String)
data class AllDebridLink(val url: String, val filename: String?, val size: Long?)

/** Direct official API; credentials never go through an application backend. */
class AllDebridClient(private val transport: AllDebridTransport) {
    private suspend fun call(endpoint: String, key: String, fields: List<Pair<String, String>> = emptyList()): JsonObject {
        require(key.isNotBlank()) { "Missing AllDebrid key" }
        val reply = transport.request(endpoint, key.trim(), fields)
        require(reply.body.length <= 2_000_000) { "AllDebrid response too large" }
        val root = Json.parseToJsonElement(reply.body).jsonObject
        check(reply.status in 200..299 && root["status"]?.jsonPrimitive?.content == "success") {
            "AllDebrid: " + ((root["error"] as? JsonObject)?.get("code") as? JsonPrimitive)?.content.orEmpty().take(80)
        }
        return root["data"]?.jsonObject ?: error("Missing AllDebrid response data")
    }
    suspend fun validate(key: String): Boolean = call("user", key)["user"] is JsonObject
    suspend fun files(key: String, magnet: String): List<AllDebridFile> {
        val uploaded = call("magnet/upload", key, listOf("magnets[]" to magnet))["magnets"]?.jsonArray?.singleOrNull()?.jsonObject
            ?: error("Missing AllDebrid magnet")
        check(uploaded["error"] == null) { "AllDebrid rejected the magnet" }
        if (uploaded["ready"]?.jsonPrimitive?.booleanOrNull != true) return emptyList()
        val id = uploaded["id"]?.jsonPrimitive?.content ?: error("Missing AllDebrid magnet ID")
        val item = call("magnet/files", key, listOf("id[]" to id))["magnets"]?.jsonArray
            ?.map { it.jsonObject }?.firstOrNull { it["id"]?.jsonPrimitive?.content == id }
            ?: error("Missing AllDebrid files")
        check(item["error"] == null) { "AllDebrid files unavailable" }
        return flattenAllDebridFiles(item["files"]?.jsonArray ?: JsonArray(emptyList()))
    }
    suspend fun unlock(key: String, file: AllDebridFile): AllDebridLink {
        val data = call("link/unlock", key, listOf("link" to file.link))
        val link = data["link"]?.jsonPrimitive?.content.orEmpty()
        check(link.startsWith("https://") || link.startsWith("http://")) { "AllDebrid link not ready" }
        return AllDebridLink(link, data["filename"]?.jsonPrimitive?.content,
            data["filesize"]?.jsonPrimitive?.longOrNull)
    }
}

fun flattenAllDebridFiles(files: JsonArray): List<AllDebridFile> = buildList {
    fun walk(items: JsonArray, prefix: String, depth: Int) {
        require(depth <= 32 && size <= 10000) { "AllDebrid file tree too large" }
        for (element in items) {
            val item = element.jsonObject
            val name = item["n"]?.jsonPrimitive?.content.orEmpty()
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            val children = item["e"] as? JsonArray
            if (children != null) walk(children, path, depth + 1)
            else {
                require(size < 10000) { "Too many AllDebrid files" }
                add(AllDebridFile(path, item["s"]?.jsonPrimitive?.longOrNull ?: 0,
                    item["l"]?.jsonPrimitive?.content.orEmpty()))
            }
        }
    }
    walk(files, "", 0)
}
