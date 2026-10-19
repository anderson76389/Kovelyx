package com.nuvio.app.features.profiles

import com.nuvio.app.core.cloud.CloudflareAccountClient
import com.nuvio.app.core.cloud.CloudflareConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual object ProfileCloudMediaTransfer {
    private const val LIMIT=20L * 1024 * 1024
    private val mimeTypes=mapOf(
        "jpg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "mp4" to "video/mp4",
        "webm" to "video/webm", "mov" to "video/quicktime"
    )

    private fun request(profileId:String, background:Boolean):HttpURLConnection {
        require(profileId.matches(Regex("[0-9a-fA-F-]{36}")))
        val session=checkNotNull(CloudflareAccountClient.currentSession) { "Session Cloudflare absente" }
        val type=if(background) "background" else "avatar"
        val endpoint=CloudflareConfig.URL.trimEnd('/') +
            "/v1/media?profileId=$profileId&kind=$type"
        require(endpoint.startsWith("https://"))
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout=20000
            readTimeout=45000
            instanceFollowRedirects=false
            setRequestProperty("Authorization","Bearer ${session.bearerToken}")
            setRequestProperty("Accept","*/*")
        }
    }

    actual suspend fun upload(profileId:String, mediaUri:String, background:Boolean) {
        if(!ProfileLocalImages.isManagedUri(mediaUri)) return
        withContext(Dispatchers.IO) {
            val file=File(URI(mediaUri))
            val ext=file.extension.lowercase()
            val mime=mimeTypes[ext] ?: error("Média non compatible R2")
            require(file.isFile && file.length() in 1..LIMIT) { "Média trop volumineux" }
            val connection=request(profileId,background)
            try {
                connection.requestMethod="PUT"
                connection.doOutput=true
                connection.setRequestProperty("Content-Type",mime)
                connection.setFixedLengthStreamingMode(file.length())
                connection.outputStream.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                check(connection.responseCode in 200..299) {
                    "R2: upload HTTP ${connection.responseCode} (média toujours conservé localement)"
                }
            } finally { connection.disconnect() }
        }
    }

    actual suspend fun download(profileId:String, background:Boolean, remotePath:String):String? =
        withContext(Dispatchers.IO) {
            if(remotePath.isBlank()) return@withContext null
            val root=mediaRoot()
            root.mkdirs()
            val hash=MessageDigest.getInstance("SHA-256")
                .digest(("$profileId|$remotePath").toByteArray())
                .take(16).joinToString("") { "%02x".format(it) }
            val ext=remotePath.substringAfterLast('.', "").lowercase()
            if(ext !in mimeTypes.keys) return@withContext null
            val target=File(root,(if(background) "background-" else "avatar-")+"cloud-$hash.$ext")
            if(target.isFile && target.length() in 1..LIMIT) return@withContext target.toURI().toString()
            val connection=request(profileId,background)
            try {
                if(connection.responseCode==404) return@withContext null
                check(connection.responseCode==200) { "R2: lecture HTTP ${connection.responseCode}" }
                val remoteMime=connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim().orEmpty()
                require(mimeTypes[ext]==remoteMime) { "Type R2 inattendu" }
                val temp=File(root,"incoming-${UUID.randomUUID()}.tmp")
                try {
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer=ByteArray(16384)
                            var size=0L
                            while(true) {
                                val count=input.read(buffer)
                                if(count<0) break
                                size+=count
                                check(size<=LIMIT) { "Média R2 trop volumineux" }
                                output.write(buffer,0,count)
                            }
                        }
                    }
                    check(temp.length() in 1..LIMIT)
                    check(temp.renameTo(target)) { "Échec du cache média privé" }
                } finally { temp.delete() }
                target.toURI().toString()
            } finally { connection.disconnect() }
        }

    private fun mediaRoot():File = File(checkNotNull(ProfileLocalImages.mediaContext).filesDir, "kovelyx/profile-media")
}
